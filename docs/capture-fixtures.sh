#!/usr/bin/env bash
# Captures 50 beacons and 200 encrypted data frames from your own Wi-Fi network into the test fixtures.
# A second device must be connected to that network and generating traffic (e.g. a video) for the data frames.
# Usage: sudo bash docs/capture-fixtures.sh <ssid> [iface] [module]   (defaults: wlp4s0 rtw88_8822ce)
# Log: ~/capture-fixtures.log (other networks appear only as counts, never by name or address).
set -u

SSID="${1:?usage: sudo bash docs/capture-fixtures.sh <ssid> [iface] [module]}"
IF="${2:-wlp4s0}"
MOD="${3:-rtw88_8822ce}"
OWNER="${SUDO_USER:-$(logname)}"
HOME_DIR="$(getent passwd "$OWNER" | cut -d: -f6)"
OUT_DIR="$(cd "$(dirname "$0")/.." && pwd)/backend/src/test/resources/fixtures"
OUT="$OUT_DIR/beacons-wpa2.pcap"
OUT_DATA="$OUT_DIR/wpa2-data.pcap"
LOG="$HOME_DIR/capture-fixtures.log"

if [[ $EUID -ne 0 ]]; then echo "Run with sudo."; exit 1; fi
mkdir -p "$OUT_DIR"
exec > >(tee "$LOG") 2>&1

restore() {
    echo "== restoring managed mode"
    ip link set "$IF" down 2>/dev/null
    iw dev "$IF" set type managed 2>/dev/null
    ip link set "$IF" up 2>/dev/null
    nmcli dev set "$IF" managed yes 2>/dev/null
    for f in "$OUT" "$OUT_DATA"; do [[ -f "$f" ]] && chown "$OWNER:$OWNER" "$f"; done
    chown "$OWNER:$OWNER" "$LOG"
    echo "== done. Internet should be back in 10-20 s."
}

wait_for_iface() {
    for _ in $(seq 1 50); do
        [[ -d /sys/class/net/$IF/phy80211 ]] && return 0
        sleep 0.2
    done
    echo "!! $IF did not reappear after reloading $MOD"; return 1
}

retry() {
    for _ in $(seq 1 10); do "$@" 2>/dev/null && return 0; sleep 0.5; done
    echo "!! failed: $*"; return 1
}

set_channel() {
    ip link set "$IF" up
    retry iw dev "$IF" set channel "$1"
}

frames_from_ap() {   # count of frames sent by $B on the current channel within $1 seconds
    timeout "$1" tcpdump -i "$IF" -Z root -n "wlan addr2 $B" 2>/dev/null | wc -l
}

echo "== $(date '+%F %T')  ssid=$SSID iface=$IF module=$MOD"

# 1. Current BSSID and channel of the network (phone hotspots get a new BSSID on every restart).
read -r B CH < <(nmcli -f BSSID,CHAN,SSID dev wifi list --rescan yes \
    | awk -v s="$SSID" 'NR > 1 { b = $1; c = $2; $1 = ""; $2 = ""; sub(/^ +/, ""); sub(/ +$/, ""); if ($0 == s) { print b, c; exit } }')
if [[ -z "${B:-}" ]]; then
    echo "!! network '$SSID' not found in the scan. Check the name and run again."
    exit 1
fi
echo "== network found: BSSID=$B channel=$CH"
trap restore EXIT

# 2. Monitor mode (driver reload first, required on the RTL8822CE).
echo "== monitor mode"
modprobe -r "$MOD" && modprobe "$MOD" || exit 1
wait_for_iface || exit 1
retry nmcli dev set "$IF" managed no || exit 1
ip link set "$IF" down && iw dev "$IF" set type monitor && ip link set "$IF" up || exit 1
set_channel "$CH" || exit 1
iw dev "$IF" info | grep -E 'type|channel'

# 3. Sanity check: is the card receiving anything at all, and is the network heard?
any=$(timeout 3 tcpdump -i "$IF" -Z root -n 'type mgt subtype beacon' 2>/dev/null | wc -l)
mine=$(frames_from_ap 3)
echo "== channel $CH: $any beacons from all networks in 3 s, $mine frames from the network"

# 4. If the network is silent on that channel, look for it on 1-13.
if [[ $mine -eq 0 ]]; then
    echo "== network not heard on channel $CH, searching 1-13"
    CH=""
    for ch in $(seq 1 13); do
        set_channel "$ch" || continue
        n=$(frames_from_ap 2)
        echo "   channel $ch: $n frames from the network"
        if [[ $n -gt 0 && -z "$CH" ]]; then CH=$ch; fi
    done
    if [[ -z "$CH" ]]; then
        echo "!! network not heard on any channel. Note: an iPhone hotspot sends no beacons while no client is connected."
        exit 1
    fi
    set_channel "$CH" || exit 1
    echo "== using channel $CH"
fi

# 5. Capture 50 beacons (gives up after 60 s).
echo "== capturing 50 beacons from $B on channel $CH"
rm -f "$OUT"
timeout 60 tcpdump -i "$IF" -Z root -n -w "$OUT" --print -c 50 "wlan addr2 $B and type mgt subtype beacon"
count=$(tcpdump -r "$OUT" -n 2>/dev/null | wc -l)
echo "== saved $count beacons to $OUT ($(stat -c %s "$OUT") bytes)"

if [[ $count -eq 0 ]]; then
    echo "== diagnostics (counts only)"
    iw dev "$IF" info | grep -E 'type|channel'
    echo "   all frames on channel $CH in 5 s:  $(timeout 5 tcpdump -i "$IF" -Z root -n 2>/dev/null | wc -l)"
    echo "   all beacons on channel $CH in 5 s: $(timeout 5 tcpdump -i "$IF" -Z root -n 'type mgt subtype beacon' 2>/dev/null | wc -l)"
    echo "   frames to/from network in 5 s:     $(timeout 5 tcpdump -i "$IF" -Z root -n "wlan addr1 $B or wlan addr2 $B or wlan addr3 $B" 2>/dev/null | wc -l)"
    echo "   frame types from network in 5 s:"
    timeout 5 tcpdump -i "$IF" -Z root -n -e "wlan addr2 $B" 2>/dev/null \
        | grep -oE 'Beacon|Probe Response|Data|QoS Data|Action|Acknowledgment|Clear-To-Send' | sort | uniq -c | sed 's/^/      /'
    echo "   rtw88 kernel messages:"
    dmesg | grep -i rtw | tail -8 | sed 's/^/      /'
fi

# 6. Encrypted data frames to/from the same network (a second device must be generating traffic).
if [[ $count -gt 0 ]]; then
    echo "== capturing 200 data frames to/from $B on channel $CH"
    rm -f "$OUT_DATA"
    timeout 60 tcpdump -i "$IF" -Z root -n -w "$OUT_DATA" -c 200 "type data and (wlan addr1 $B or wlan addr2 $B or wlan addr3 $B)" 2>&1 | tail -3
    data=$(tcpdump -r "$OUT_DATA" -n 2>/dev/null | wc -l)
    echo "== saved $data data frames to $OUT_DATA ($(stat -c %s "$OUT_DATA") bytes)"
fi

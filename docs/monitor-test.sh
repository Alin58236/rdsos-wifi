#!/bin/bash
# Monitor-mode hopper test: reload the Wi-Fi driver, hop channels 1-13 at 250 ms for 20 s,
# count beacons per real channel (IE 3), then check a 5 GHz detour. Prints counts only (no SSIDs/MACs).
# Usage: sudo bash docs/monitor-test.sh [interface] [driver-module]   (defaults: wlp4s0 rtw88_8822ce)
# Wi-Fi drops while it runs; managed mode is restored at the end.
IF=${1:-wlp4s0}
MODULE=${2:-rtw88_8822ce}
USER_NAME=${SUDO_USER:-$USER}
[ -d "/sys/class/net/$IF/wireless" ] || { echo "no wireless interface named $IF"; exit 1; }
OUT=$(getent passwd "$USER_NAME" | cut -d: -f6)/wifi-hop-test.txt
CAP=$(mktemp)
exec > >(tee "$OUT") 2>&1

restore() {
  [ -n "$HOP" ] && kill "$HOP" 2>/dev/null
  ip link set "$IF" down
  iw dev "$IF" set type managed
  ip link set "$IF" up
  nmcli dev set "$IF" managed yes
  rm -f "$CAP"
  chown "$USER_NAME": "$OUT"
}
trap restore EXIT

fresh_monitor() {
  modprobe -r "$MODULE"
  modprobe "$MODULE"
  for _ in $(seq 1 50); do ip link show "$IF" >/dev/null 2>&1 && break; sleep 0.2; done
  sleep 1
  nmcli dev set "$IF" managed no
  ip link set "$IF" down
  iw dev "$IF" set type monitor
  ip link set "$IF" up
}

summary() {
  echo "  beacons=$(grep -c . "$CAP") bssids=$(grep -o 'BSSID:[0-9a-f:]*' "$CAP" | sort -u | wc -l)"
  echo "  per beacon-CH:"; grep -o 'CH: [0-9]*' "$CAP" | sort -k2 -n | uniq -c | sed 's/^/   /'
  echo "  per radiotap MHz:"; grep -o '[0-9]\{4\} MHz' "$CAP" | sort | uniq -c | sed 's/^/   /'
}

echo "kernel $(uname -r)"
fresh_monitor

echo "== 1: hop 1-13 @250ms for 20 s =="
( while true; do for ch in $(seq 1 13); do iw dev "$IF" set channel $ch; sleep 0.25; done; done ) &
HOP=$!
timeout 20 tcpdump -i "$IF" -e -l -nn 'type mgt subtype beacon' 2>/dev/null > "$CAP"
kill $HOP; wait $HOP 2>/dev/null; HOP=
summary

echo "== 2: 5 GHz detour, then back to 2.4 (no reload) =="
iw dev "$IF" set channel 36; sleep 0.5
timeout 3 tcpdump -i "$IF" -e -l -nn 'type mgt subtype beacon' 2>/dev/null > "$CAP"
echo " ch36:"; summary
iw dev "$IF" set channel 6; sleep 0.5
timeout 4 tcpdump -i "$IF" -e -l -nn 'type mgt subtype beacon' 2>/dev/null > "$CAP"
echo " back to ch6:"; summary
echo "done"

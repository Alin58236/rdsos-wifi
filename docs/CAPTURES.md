# Capturing test fixtures (do this once, before writing `RadiotapParser`)

One sitting, about 30 minutes. Run everything in a **normal terminal** (Ctrl+Alt+T), not through Claude Code: `sudo` needs a terminal, and Wi-Fi drops during parts A and C.

Only capture **your own** networks. Every command filters on your own network's BSSID, so the files contain no third-party traffic and are safe to commit.

Files go to `backend/src/test/resources/fixtures/`:

| File | Source | Used to test |
|---|---|---|
| `beacons-wpa2.pcap` | Your own router (2.4 GHz), monitor mode | Radiotap, 802.11 management header, IEs, WPA2 security, channel |
| `wpa2-data.pcap` | Your own router + a second device, monitor mode | Data frames with the Protected bit → "Encrypted" |
| `open-http.pcap` | Laptop as an open AP + `mon0` | Monitor-mode path: Radiotap → 802.11 → LLC/SNAP → IPv4 → TCP → HTTP |
| `managed-http.pcap` | Laptop connected normally | Managed-mode path: Ethernet → IPv4 → TCP → HTTP |

```bash
cd ~/Desktop/rdsos-wifi/backend/src/test/resources && mkdir -p fixtures && cd fixtures
```

## A. Your own router (2.4 GHz), monitor mode
Use your **own router's 2.4 GHz network**, not a phone hotspot: **an iPhone hotspot sends no beacons while no client is connected** (it only answers active scans), and on 2026-11-22 it sent almost nothing on its advertised channel even with an iPad connected. A router beacons continuously.

The script finds the network's current BSSID and channel, switches to monitor mode (with the driver reload), captures 50 beacons and 200 data frames filtered on that BSSID, prints diagnostics if nothing arrives, and always restores managed mode.

1. Connect a **second device** (iPad, phone) to the router's **2.4 GHz** network and keep it busy (e.g. play a video). Check it hasn't switched to the 5 GHz network.
2. Run it in a normal terminal (no internet while it runs, about 1 minute):
   ```bash
   sudo bash docs/capture-fixtures.sh <your-2.4GHz-SSID>
   ```
3. Read `~/capture-fixtures.log`: it should end with `saved 50 beacons` and `saved 200 data frames`.

Captured on 2026-11-22 (router `HomeNetwork`, channel 10): Radiotap Flags has FCS (0x10); security is mixed WPA/WPA2-Personal (RSN IE 48 with AKM 2 and TKIP group cipher, plus WPA vendor IE 221); 175 of the 200 data frames are Protected.

## B. Managed mode HTTP
1. Connect the laptop to Wi-Fi as usual (home network or the iPhone hotspot).
2. Two terminals:
   ```bash
   sudo tcpdump -i wlp4s0 -w managed-http.pcap -c 40 'tcp port 80'    # terminal 1
   curl http://neverssl.com                                              # terminal 2, run it 2-3 times
   ```

## C. Laptop as an open AP (monitor-mode HTTP, and the feasibility test)
iPhones can't run an open hotspot, so the laptop becomes the open network, and a second virtual interface `mon0` on the same card captures in monitor mode. The iPhone is the client. **The laptop has no internet during this part.**

1. Start the AP, the monitor interface and a tiny web server:
   ```bash
   sudo modprobe -r rtw88_8822ce && sudo modprobe rtw88_8822ce && sleep 3
   sudo nmcli con add type wifi ifname wlp4s0 con-name rdsos-demo autoconnect no ssid RDSOS-DEMO \
     802-11-wireless.mode ap 802-11-wireless.band bg 802-11-wireless.channel 6 ipv4.method shared
   sudo nmcli con up rdsos-demo
   sudo iw dev wlp4s0 interface add mon0 type monitor && sudo ip link set mon0 up
   mkdir -p /tmp/www && echo '<h1>RDSOS test</h1>' > /tmp/www/index.html
   sudo python3 -m http.server 80 --bind 10.42.0.1 --directory /tmp/www &
   ```
   (`nmcli con add` is only needed the first time; later just `sudo nmcli con up rdsos-demo`.)
2. iPhone: Wi-Fi → join **RDSOS-DEMO** (open; ignore "No Internet Connection") → Safari → `http://10.42.0.1/`.
3. Feasibility check:
   ```bash
   sudo tcpdump -i mon0 -e -c 30 'tcp port 80'
   ```
   - **Pass:** lines with `2437 MHz`, addresses `10.42.0.x`, and `HTTP: GET / HTTP/1.1`.
   - **Fail** (nothing, or no Radiotap info): stop here, undo (step 5), and tell Claude; a plan B is needed.
4. If it passed, save the fixture (reload the page on the iPhone while it runs):
   ```bash
   B=$(cat /sys/class/net/wlp4s0/address)
   sudo tcpdump -i mon0 -w open-http.pcap -c 300 "wlan addr1 $B or wlan addr2 $B or wlan addr3 $B"
   ```
5. Undo:
   ```bash
   sudo pkill -f 'http.server 80'; sudo iw dev mon0 del; sudo nmcli con down rdsos-demo
   ```

## D. Finish
```bash
sudo chown $USER:$USER *.pcap
ls -l
```
Open each file in Wireshark and check it looks right: the expected frame types and no other networks. Wireshark is also where you read the **expected values** for your parser tests (e.g. Radiotap length, signal dBm, SSID bytes).

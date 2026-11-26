# Demo script (Fri 2026-11-27)

The demo shows the requirements in `docs/requirements.md` in this order: the Wi-Fi Analyzer (Project 1), the sniffer in monitor mode, then the sniffer in managed mode (Project 2). It takes about 10 minutes. Plan B (replay from files) is at the end.

## 0. The day before

- [ ] Laptop booted into **Ubuntu**, charger packed.
- [ ] Backend jar built: `cd backend && ./mvnw package` (runs the tests too).
- [ ] Frontend dependencies installed: `cd frontend && npm install`.
- [ ] **An open test network on 2.4 GHz**, needed for IP/TCP/HTTP in monitor mode:
  - the router's **guest network with no password**, forced to 2.4 GHz, or
  - an **Android** hotspot with security "None" and the band set to 2.4 GHz. An iPhone hotspot always has a password, so it does not work for this.
  - Write down its SSID and channel (it appears in the AP table).
- [ ] **A second device** (phone) that can join the open network and open `http://neverssl.com`.
- [ ] A backup capture of the open network, kept **outside the repo** (it may contain neighbours' beacons):
  ```bash
  mkdir -p ~/rdsos-demo
  # in monitor mode, pinned on the open network's channel, while the phone loads http://neverssl.com
  sudo tcpdump -i wlp4s0 -w ~/rdsos-demo/live-demo.pcap -c 2000
  ```
- [ ] One full rehearsal of sections 2–4.

## 1. Start (5 minutes before)

```bash
# terminal 1: backend (root: libpcap capture and ip/iw/nmcli/modprobe need it).
# Start it from backend/, so the replay folder (src/test/resources/fixtures) resolves.
cd ~/Desktop/rdsos-wifi/backend
sudo java --enable-native-access=ALL-UNNAMED -jar target/wifianalyzer-0.0.1-SNAPSHOT.jar

# terminal 2: frontend
cd ~/Desktop/rdsos-wifi/frontend
npm run dev
```

Open `http://localhost:5173`. The header badge must say **Backend connected**.

## 2. Project 1: Wi-Fi Analyzer (monitor mode)

1. **Interface** `wlp4s0` → **Monitor mode**. This takes a few seconds: the backend reloads the `rtw88_8822ce` driver, takes the interface away from NetworkManager and sets type monitor. The laptop drops off Wi-Fi; the requirement says the interface must not be connected to a network.
2. **Start live**. The channel hopper visits channels 1–13 every 250 ms (the header shows the current channel).
3. Within ~5 seconds the **AP table** and the **channel graph** fill up.

Talking points:
- Each network has the 7 required fields: SSID, BSSID (MAC), manufacturer, signal, channel, frequency, security. The icon colour follows the signal: green ≥ −60 dBm, yellow −60 … −85, red < −85.
- Everything comes from **beacons parsed by hand**: Radiotap (signal, FCS flag) → 802.11 header (type 0 / subtype 8, BSSID) → beacon body (privacy bit) → information elements (SSID = IE 0, channel = IE 3, RSN = IE 48, WPA = IE 221).
- The channel comes from IE 3, not from the radio frequency, because beacons from neighbouring channels leak in.
- The manufacturer comes from the bundled IEEE OUI file, with no network lookup. Random MACs (bit 0x02 of the first byte) show as `Local/Unknown`.
- libpcap only opens the interface and returns raw bytes; it is called from Java through the FFM API, without JNI or a capture library.

## 3. Project 2: sniffer in monitor mode

1. Keep the capture running. In **Channel**, pin the open network's channel. The hopper pauses; otherwise it would hear only about 1/13 of the traffic.
2. On the phone: join the open network and open `http://neverssl.com`.
3. In **Packets**, click a green **HTTP** row (`GET / HTTP/1.1` or `HTTP/1.1 200 OK`).
4. The layer tree shows **Radiotap → 802.11 Data → LLC/SNAP → IPv4 → TCP → HTTP**. Click each layer: its bytes light up in the hex dump.
5. Show a grey row from the WPA2 home network: **Encrypted (WPA2)**. The 802.11 header (MAC addresses) is readable; the rest is CCMP ciphertext, so decoding stops there.

Talking points:
- 802.11 addresses depend on the ToDS/FromDS bits: phone → AP is ToDS (addr1 = BSSID, addr2 = source, addr3 = destination).
- LLC/SNAP (`AA AA 03 00 00 00` + EtherType) tells which protocol follows; `0x0800` = IPv4.
- IPv4 and TCP: **every header field** is shown, in wire order. Options are shown as raw hex (decoding them was dropped from the scope).
- HTTP is decoded when either port is 80 and the TCP segment has a payload. The payload length is `IPv4 total length − IPv4 header − TCP header`, not "the rest of the frame", because the frame may end with the FCS or padding.

## 4. Project 2: sniffer in managed mode

1. **Stop**, then **Managed mode** on `wlp4s0`.
2. Connect the laptop to the home Wi-Fi (top bar, or `nmcli dev wifi connect <SSID>`).
3. **Clear** the packet list, then **Start live** on `wlp4s0`.
4. In a terminal: `curl http://neverssl.com`.
5. Click the `GET` row: the layers are **Ethernet (managed mode) → IPv4 → TCP → HTTP**.

Talking points:
- In managed mode the kernel removes the 802.11 header and decrypts the traffic, so libpcap delivers **Ethernet** frames (link type 1). The UI says "Ethernet (managed mode)" for that reason.
- The decoder picks the first parser from the link type libpcap reports: 127 = Radiotap, 1 = Ethernet.
- Only the laptop's own traffic is visible, but decrypted.
- Optional, to prove the parsing: run `sudo tcpdump -i wlp4s0 -nn -vv -X port 80` next to it and compare TTL, identification, sequence numbers and flags.

## 5. Plan B: replay from files

If live capture fails, replay files show the same parsing (no root needed for this part):

- **Replay a file** → `beacons-wpa2.pcap`: AP table and graph (one network, `HomeNetwork`).
- `wpa2-data.pcap`: 802.11 data frames, `Encrypted (WPA2)` and `Null` frames.
- `managed-http.pcap`: the managed-mode GET and `200 OK`, with every IPv4/TCP field.
- The backup capture from section 0: restart the backend with
  `--wifi.replay-dir=/home/alin/rdsos-demo` added after `-jar target/...jar`. `live-demo.pcap` then appears in the list.
- Last resort, without the backend: `http://localhost:5173/?mock` (sample networks and four real packets).

## 6. Troubleshooting

| Symptom | Check / fix |
|---|---|
| Badge says **Backend disconnected** | Terminal 1: is the backend running? It must run as root and listen on 127.0.0.1:8080. |
| `frames` in the Packets card does not increase | The capture is not receiving. `iw dev wlp4s0 info` must show `type monitor` (or `managed` for section 4). |
| Monitor mode, but no networks on 2.4 GHz | The driver reload did not happen. Switch to Managed and back to Monitor, or by hand: `sudo modprobe -r rtw88_8822ce && sudo modprobe rtw88_8822ce`. |
| "A capture is already running" | Press **Stop** first. |
| Pin channel fails with "Channel hopper is not running" | Pinning works only during a live capture in monitor mode. |
| No HTTP rows in managed mode | Use `curl`, not a browser (browsers upgrade to HTTPS). Port 443 traffic shows as TCP only. |
| No HTTP rows in monitor mode | Is the channel pinned to the open network's channel? Is the phone really on the **open** network (not the WPA2 one)? |
| The laptop has no internet afterwards | **Managed mode** in the UI, or stop the backend with Ctrl+C: on shutdown it switches every interface it put in monitor mode back to managed. |

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Master's course project (RDSOS): a **Wi-Fi Analyzer** (similar to Android WiFi Analyzer) and an **802.11 packet sniffer** (a cut-down tcpdump/Wireshark), built as one localhost app. The full spec is in `docs/requirements.md`. Read it before any feature work; it is the grading rubric.

**The grading rule shapes the whole design:** the grade scales with how much is implemented by hand. OS and library calls must do only minimal work (open the capture, return raw bytes). All header parsing is done by hand on `byte[]`.
- Never use library parsers for Radiotap, 802.11, LLC/SNAP, Ethernet, IPv4, TCP or HTTP: no pcap4j/jnetpcap packet classes, no `InetAddress` parsing, no HTTP libraries.
- Only skip Project 3 if Project 1 uses real Monitor mode with manually parsed management frames. Don't fall back to an OS Wi-Fi scan API.

## Working mode

**The user writes the code; Claude guides.** Explain, propose structure, point to reading, review, answer questions. Don't implement backend/frontend files unless the user asks for a specific piece. Explanations go in **Romanian, with technical terms left in English** (carrier, frame, header, beacon...); code and technical docs (CLAUDE.md, HANDOFF.md) stay in English; personal study notes (e.g. `docs/info_for_project.md`) are in Romanian. Session history and next steps are in `docs/HANDOFF.md`.

## Status

Pre-scaffold. The layout and commands below are the agreed target; update this file once they exist.

**Machines:** the main dev machine is a Windows desktop with **no Wi-Fi adapter**, so parser work and tests there run against `.pcap` fixtures only. Live capture runs on an Asus TUF FX505DT laptop (planned Linux dual boot).

**Open question:** whether the laptop's built-in Wi-Fi card supports monitor mode is not yet verified. Monitor mode needs Linux (dual boot, live USB, or a VM with a USB adapter passed through) and a chipset that supports monitor mode. Check with `iw list` that "Supported interface modes" includes `monitor`.

## Stack and layout (target)

- `backend/`: Spring Boot, **Java 25**, Gradle wrapper. Calls libpcap through the **Java FFM API** (`java.lang.foreign`) with no capture library.
- `frontend/`: Vite + React + TypeScript + shadcn/ui. A localhost tool, so no Next.js and no website launch checklist.
- `docs/`: requirements and notes.

## Commands (target)

```bash
# backend (capture needs root or CAP_NET_RAW+CAP_NET_ADMIN)
cd backend && ./gradlew test
cd backend && ./gradlew test --tests "*RadiotapParserTest"
cd backend && ./gradlew bootJar && sudo java -jar build/libs/*.jar

# frontend (dev server proxies /api and /ws to the backend)
cd frontend && npm install && npm run dev
cd frontend && npm run lint && npm run build

# manual interface checks on the Linux box
iw list                                   # confirm monitor mode support
sudo nmcli dev set <if> managed no        # stop NetworkManager from reverting the mode
sudo ip link set <if> down && sudo iw dev <if> set type monitor && sudo ip link set <if> up
sudo tcpdump -i <if> -y IEEE802_11_RADIO -c 20   # sanity check: beacons visible
```

## Architecture

### Capture pipeline
1. `capture/`: FFM bindings to libpcap (`pcap_create`, `pcap_set_rfmon`, `pcap_activate`, `pcap_datalink`, `pcap_next_ex`, `pcap_close`). A dedicated capture thread copies each frame into `byte[]` together with its timestamp and **link type**, then writes it to a bounded ring buffer.
2. Dispatch is by link type, which depends on the interface mode:
   - `127` `DLT_IEEE802_11_RADIO` (Monitor): Radiotap → 802.11.
   - `1` `DLT_EN10MB` (Managed): Ethernet II. The kernel strips the 802.11 header in managed mode, so the UI must say "Ethernet header (managed mode)" and not show it as 802.11.
3. `parse/`: one parser per layer, each a pure function from `(byte[], offset)` to an immutable record plus the next offset. Covers Radiotap, 802.11 (management/data frame control, addresses by ToDS/FromDS), LLC/SNAP, Ethernet, IPv4 (all fields, options by IHL), TCP (all fields, options by data offset), and HTTP (ASCII payload when either port is 80).
4. `wifi/`: management frames (beacon, probe response) update an AP registry keyed by BSSID:
   - SSID from IE 0; channel from IE 3 or derived from the Radiotap frequency.
   - Signal from the Radiotap antenna signal (dBm).
   - Manufacturer from a **bundled IEEE OUI file** (no network lookups).
   - Security from the Capability Info privacy bit (WEP), RSN IE 48 (WPA2/WPA3; AKM 8 = SAE), and vendor IE 221 with OUI `00:50:F2` type 1 (WPA).
5. **Channel hopper:** in Monitor mode it cycles channels 1–13 (`iw dev <if> set channel N`, ~250 ms) so the AP list and channel graph cover every channel. It pauses when the user pins a channel for sniffing.
6. `api/`: REST for control (list interfaces, switch mode, start/stop, pin channel). WebSocket pushes **batched** updates about every 200 ms (never one message per packet) for AP snapshots and parsed packets.

### Parsing gotchas
- **Byte order:** Radiotap and 802.11 fields are little-endian; IPv4/TCP are big-endian (network order). Use explicit helpers, never platform order.
- **Radiotap:** walk the `present` bitmask including extended bitmasks (bit 31). Each field has a **natural alignment** relative to the start of the Radiotap header; pad before reading. The header length is in bytes 2–3. Respect it even if unknown fields remain.
- **Address roles:** 802.11 address meaning depends on the ToDS/FromDS bits. QoS data frames add 2 bytes; HT Control adds 4 more when the Order bit is set.
- **Encrypted traffic:** data frames with the Protected bit set (WPA2) can't be parsed past the 802.11 header. Show them as encrypted. Demo IP/TCP/HTTP in Monitor mode with an **open** test network and plain-HTTP traffic from a second device.

### Frontend
- AP list: signal icon colour by RSSI (green ≥ −60 dBm, yellow −60…−75, red < −75), plus SSID, BSSID, vendor, dBm, channel, MHz and security.
- Channel graph: **custom SVG** with a curve per AP spanning about ±2 channels around its centre and height = RSSI, the same look as the Android app.
- Packet view: virtualized list, a detail pane with a layer tree, and a hex dump.

## Testing

- TDD on parsers. Fixtures are real `.pcap` files in `backend/src/test/resources/fixtures/`, read by a small hand-written pcap-file reader in test utils (24-byte global header plus 16-byte record headers, so no library).
- Only capture fixtures from your own hotspot or test network. Captures contain third-party MACs/SSIDs and must not include other people's traffic.

## Security

- The backend runs privileged, so bind to `127.0.0.1` only and restrict CORS to the frontend dev origin.
- Interface names from the API must match the system's interface list before they're passed to `iw`/`ip`/`nmcli`. Use `ProcessBuilder` with argument arrays, never a shell string.

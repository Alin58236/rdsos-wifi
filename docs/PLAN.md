# RDSOS Wi-Fi Analyzer + Sniffer — Implementation Plan (demo in 7 days)

## Context
The project is a course project. Monitor mode now works on the laptop's RTL8822CE after a driver reload (PR #2). The toolchain is installed: JDK 25, Maven, Node 26, libpcap-dev. The repo has docs only; no code exists yet.

Decisions this session:
- Capture: libpcap called through the Java FFM API, with no capture library.
- Build: Maven. Frontend: React + TypeScript (Vite, shadcn/ui).
- Base package: `com.rdsos.wifianalyzer`.
- **The demo is in 7 days (around Fri 2026-11-27) and the user works part-time.**
- New working mode, **split by grade weight**:
  - **U (user)** writes the graded core: FFM capture, the pcap file reader, all parsers (Radiotap → HTTP), and the AP and security logic.
  - **C (Claude)** writes the plumbing: Spring wiring, interface and mode control, the channel hopper, REST/WebSocket, and the whole React UI.
  - C reviews all of U's code.

Goal: one localhost app with all required features in both modes, and a rehearsed demo.

## Architecture (decisions that make 7 days feasible)
- **`CaptureSource` interface** with `open() / next() → RawFrame | null / close()` and three implementations:
  - `PcapFileSource`: hand-written `.pcap` reader (U). Used for tests and for developing on the Wi-Fi-less desktop. It is also the **demo fallback**: replay a recorded capture if the hardware misbehaves.
  - `PcapLiveSource`: FFM binding to libpcap (U).
  - (Raw `AF_PACKET` socket: out of scope.)
- `RawFrame` (`@Value`): `long tsMicros, int linkType, int origLen, byte[] data`.
- **Monitor mode is set by our own commands, not libpcap.** Do *not* call `pcap_set_rfmon`: on Linux it can create a separate `monN` interface. The sequence is: reload the driver, `nmcli managed no`, `ip link down`, `iw set type monitor`, `ip link up`. Then `pcap_create` on `wlp4s0`. The link type comes back as 127 (Radiotap) in monitor mode and 1 (Ethernet) in managed mode.
- **Threads:**
  - Capture thread: a platform thread, because it blocks inside native code. Set `pcap_set_timeout` to 100 ms so it can check its stop flag.
  - It pushes frames into an `ArrayBlockingQueue<RawFrame>(10_000)` with `offer()`, dropping when full and counting drops.
  - A decoder thread takes frames, parses them, and updates the `ApRegistry` and the `PacketStore` (the last 5,000 decoded packets).
- **WebSocket stays small:** every 200 ms, send AP snapshots plus *packet summaries* (one line per packet). The full layer tree and hex dump come from `GET /api/packets/{id}` when you click a row.
- **Layer model for the UI** (U's parsers fill it, C's UI renders it): `Layer(name, offset, length, List<Field(name, value, offset, length)>)`. The offsets let the hex view highlight the selected field.

## Package layout (`backend/src/main/java/com/rdsos/wifianalyzer/`)
| Package | Files | Owner |
|---|---|---|
| `capture/` | `CaptureSource`, `RawFrame`, `PcapFileSource`, `LibPcap` (FFM handles), `PcapLiveSource` | U |
| `capture/` | `CaptureService` (threads, queue, start/stop) | C |
| `parse/` | `ByteReader` (u8, u16/u32 LE+BE, mac, ipv4 text), `RadiotapParser`, `Dot11Parser`, `InformationElements`, `LlcSnapParser`, `EthernetParser`, `Ipv4Parser`, `TcpParser`, `HttpPayload`, `PacketDecoder` (dispatch by link type → `DecodedPacket`) | U |
| `wifi/` | `AccessPoint`, `ApRegistry`, `SecurityClassifier`, `OuiDatabase`, `ChannelMath` | U |
| `iface/` | `CommandRunner` (`ProcessBuilder`, argument arrays), `InterfaceService` (lists `/sys/class/net/*/wireless`; reads mode from `/sys/class/net/X/type`: 1 = managed, 803 = monitor; switches modes), `ChannelHopper` | C |
| `api/` | `CaptureController`, `PacketController`, `UpdatesWebSocketHandler` (raw text WS, no STOMP), `WebSocketConfig`, `BatchPublisher` (`@Scheduled` 200 ms), DTO classes (`@Value`) | C |
| resources | `application.properties` (`server.address=127.0.0.1`), `oui.txt` (IEEE registry, downloaded once and bundled) | C/U |

Maven dependencies: `spring-boot-starter-web`, `spring-boot-starter-websocket`, `spring-boot-starter-test`, and nothing else. Spring Boot is the latest 4.x from start.spring.io, with Java 25.

## Technical reference for U's parts
**FFM / libpcap (`LibPcap`, `PcapLiveSource`)**
- Calls: `pcap_create(char*, char* errbuf[256])`, `pcap_set_snaplen(p, 65535)`, `pcap_set_promisc(p, 1)`, `pcap_set_timeout(p, 100)`, `pcap_set_immediate_mode(p, 1)`, `pcap_activate`, `pcap_datalink`, `pcap_next_ex(p, pcap_pkthdr**, u_char**)` (returns 1 = packet, 0 = timeout, −1 = error, −2 = EOF), `pcap_geterr`, `pcap_close`.
- `pcap_pkthdr` on x86-64: `tv_sec` (8 bytes), `tv_usec` (8), `caplen` u32 at offset 16, `len` u32 at offset 20.
- Lookups: `SymbolLookup.libraryLookup("libpcap.so.1", arena)`, `Linker.nativeLinker().downcallHandle(...)`. Copy each packet out with `MemorySegment.reinterpret(caplen).toArray(JAVA_BYTE)` before the next call, because libpcap reuses the buffer.

**`.pcap` file reader:** 24-byte global header. Magic `a1b2c3d4` means microsecond timestamps and `a1b23c4d` means nanosecond; if the bytes come out swapped, the file is big-endian. The link type is at offset 20. Each record has a 16-byte header: `ts_sec`, `ts_usec`, `incl_len`, `orig_len`.

**Radiotap** (all little-endian): `version u8, pad u8, len u16, present u32…`
- Read `present` words while bit 31 is set. Process the words in order, bits 0–28 of each. Bit 29 resets to the Radiotap namespace. Bit 30 is a vendor namespace (`OUI 3, subns 1, skip_len u16`, 2-byte aligned); skip `skip_len` bytes.
- Align each field to its natural alignment, measured from the start of the header. Field table (bit: size/align):

  | Bit | Field | Size | Align |
  |---|---|---|---|
  | 0 | TSFT | 8 | 8 |
  | 1 | Flags | 1 | 1 |
  | 2 | Rate | 1 | 1 |
  | 3 | Channel (freq u16 + flags u16) | 4 | 2 |
  | 4 | FHSS | 2 | 1 |
  | 5 | dBm antenna signal (s8) | 1 | 1 |
  | 6 | dBm noise | 1 | 1 |
  | 7 | Lock quality | 2 | 2 |
  | 8, 9 | TX attenuation fields | 2 | 2 |
  | 10 | dBm TX power | 1 | 1 |
  | 11 | Antenna | 1 | 1 |
  | 12, 13 | dB signal / noise | 1 | 1 |
  | 14 | RX flags | 2 | 2 |
  | 15 | TX flags | 2 | 2 |
  | 16, 17 | Retries | 1 | 1 |
  | 18 | XChannel | 8 | 4 |
  | 19 | MCS | 3 | 1 |
  | 20 | A-MPDU | 8 | 4 |
  | 21 | VHT | 12 | 2 |
  | 22 | Timestamp | 12 | 8 |
  | 23, 24 | HE, HE-MU | 12 | 2 |
  | 26 | 0-length PSDU | 1 | 1 |
  | 27 | L-SIG | 4 | 2 |

- On an unknown bit, stop and jump to `len`.
- RSSI is the **first** dBm signal field (the combined value). On this card the per-antenna values follow in later namespace words.
- **If Flags has 0x10 set (FCS at end), the 802.11 frame length is `caplen − len − 4`.** Otherwise the 4 FCS bytes are parsed as a bogus trailing IE.

**802.11:**
- Frame Control is a u16 (LE): version bits 0–1, type 2–3, subtype 4–7. The flag byte holds toDS 0x01, fromDS 0x02, moreFrag 0x04, retry 0x08, pwr 0x10, moreData 0x20, **protected 0x40**, order 0x80.
- Header length:
  - Management: 24 bytes, +4 if the order bit is set.
  - Control: ACK and CTS are 10 bytes; RTS is 16.
  - Data: 24 bytes, +6 if both toDS and fromDS are set, +2 for QoS (subtype & 0x8), +4 if QoS and order are both set.
- Address roles depend on toDS/fromDS; see the table in `docs/course-notes-summary.md` §2.3.
- Beacon / probe-response body: `timestamp 8, interval 2, capability 2`, then IEs (`id u8, len u8, data`).

**Information elements and security:**
- SSID = IE 0 (if empty or all zero bytes, show it as `<hidden>`).
- Channel = IE 3. If that's missing (common on 5 GHz), use IE 61 (HT Operation, first byte = primary channel), then the Radiotap frequency. Never the tuned channel, because neighbouring channels leak in.
- RSN = IE 48: `version u16, group suite 4, pairwise count u16 + n×4, AKM count u16 + n×4`. The suite OUI is `00-0F-AC`. AKMs: 1 = 802.1X (Enterprise), 2 = PSK (WPA2-Personal), 8 = SAE (WPA3); 2 and 8 together = WPA2/WPA3 transition. Ciphers: 4 = CCMP, 2 = TKIP.
- WPA = IE 221 with OUI `00:50:F2`, type 1.
- WEP = the privacy bit with no RSN/WPA IE. Open = no privacy bit.
- `ChannelMath`: 2.4 GHz `ch = (f − 2407) / 5` (2484 → 14); 5 GHz `ch = (f − 5000) / 5`.
- `OuiDatabase`: parse the `XX-XX-XX   (hex)   Vendor` lines into a map. If the MAC's first byte has bit 0x02 set, show "Locally administered (random)".

**Sniffer layers:**
- LLC/SNAP: `AA AA 03 00 00 00` + EtherType; 0x0800 = IPv4. If the protected bit is set, stop and mark the frame "Encrypted (WPA2)".
- Ethernet (managed mode): `dst 6, src 6, EtherType 2`; skip a 4-byte 802.1Q tag on 0x8100.
- IPv4: all fields, options from IHL > 5. Verifying the header checksum is a small bonus if time allows.
- TCP: all fields, the 9 flags, options from data offset > 5. Decode option kinds 0, 1, 2, 3, 4, 5 and 8; show the rest as raw bytes.
- HTTP: when either port is 80 and there's a payload, show printable ASCII with other bytes as `.`.

## Day plan (part-time; U = user, C = Claude)
| Day | U (graded core, TDD) | C (plumbing) |
|---|---|---|
| **1 · Sat 21** | Capture fixtures (see below). `ByteReader` + tests. `PcapFileSource` + test against `beacons.pcap`. | Maven scaffold (start.spring.io settings, `mvnw`, `application.properties`, test layout). `iface/` (`CommandRunner`, `InterfaceService`, mode switch), `ChannelHopper`. |
| **2 · Sun 22** | `RadiotapParser`, `Dot11Parser` (TDD: fixtures plus hand-built byte arrays for alignment, extended bitmask, QoS, 4-address). | `CaptureService`, `BatchPublisher`, REST + WS endpoints, DTOs. Vite + React + TS + shadcn scaffold with `/api` and `/ws` proxy. |
| **3 · Mon 23** | `InformationElements`, `SecurityClassifier`, `ChannelMath`, `OuiDatabase`, `ApRegistry`. **Milestone: AP list from `beacons.pcap` through `PcapFileSource`.** | AP table (signal icon colours, all 7 fields), WS client hook, mode/interface/start/stop controls. |
| **4 · Tue 24** | `LibPcap` + `PcapLiveSource` (FFM). **Milestone: live AP list with the hopper on the laptop.** | Channel graph (custom SVG, a curve per AP spanning ±2 channels). Pin-channel control. |
| **5 · Wed 25** | `LlcSnapParser`, `EthernetParser`, `Ipv4Parser`, `TcpParser`, `HttpPayload`, `PacketDecoder` → `Layer` model. | Packet list (latest 1,000 rows, filters: all / IP / HTTP), detail tree, hex dump with field highlighting. |
| **6 · Thu 26** | Live end-to-end in both modes; fix bugs from Wireshark comparisons. | Integration fixes, `docs/DEMO.md` demo script, update `CLAUDE.md` and `HANDOFF.md`. |
| **7 · Fri 27** | Rehearse. Demo. | Final review. |

**Cut order if behind** (never cut the required features):
1. Hex field highlighting (keep the plain hex dump).
2. Decoding of rare TCP options.
3. Checksum verification.
4. Packet filters in the UI.
5. AP expiry and polish.

**Required, never cut:** all 7 AP fields, the colour icons, the 13-channel graph, 802.11 MACs, all IPv4 and TCP fields, HTTP ASCII, and both modes.

## Fixtures (Day 1, U, own phone hotspot only)
**Superseded by `docs/CAPTURES.md`** (step-by-step). The user has an iPhone, which can't run an open hotspot, so the open-network HTTP capture uses the laptop as an open AP plus a `mon0` monitor interface (part C there). `CaptureService` therefore needs an option to use an interface that is already in monitor mode as it is: no driver reload, no mode switch, no hopping.

The hotspot runs on **2.4 GHz** on a fixed channel. Filter every capture to the hotspot's BSSID so no third-party traffic is saved: `wlan addr1/addr2/addr3 <BSSID>`, or `ether host` in managed mode. Save to `backend/src/test/resources/fixtures/`:
1. `beacons-wpa2.pcap`: monitor mode, about 50 beacons from the hotspot set to WPA2.
2. `beacons-open.pcap`, plus `open-http.pcap`: hotspot set to **open**; a second device loads `http://neverssl.com`; monitor mode pinned to the hotspot's channel.
3. `managed-http.pcap`: laptop connected normally; `curl http://neverssl.com` during the capture.
4. `wpa2-data.pcap`: WPA2 hotspot, data frames (for the "Encrypted" display).

Use Wireshark on the same files as the **source of expected values** in tests.

## Security
- Bind the backend to `127.0.0.1`. Allow CORS and WebSocket origins only from `http://localhost:5173`.
- The interface name must be in the `/sys/class/net/*/wireless` list. The channel must be 1–13.
- Commands run through `ProcessBuilder` with argument arrays. The driver module name is configured in `application.properties`, never taken from the API.
- Run with `sudo java --enable-native-access=ALL-UNNAMED -jar backend/target/*.jar`.

## Files to update along the way
- `CLAUDE.md`:
  - Status: scaffolded.
  - Working mode: split by grade weight.
  - Remove `pcap_set_rfmon` from the FFM list.
  - Note `PcapFileSource` as a capture source.
  - Note that 5 GHz beacons often lack IE 3.
- `docs/HANDOFF.md`: the decisions and schedule above.
- New: `docs/DEMO.md`.

## Verification
- `cd backend && ./mvnw test`: parser tests against the fixtures, with expected values taken from Wireshark.
- **File replay:** start the app with `PcapFileSource` on a fixture (it runs on the desktop too, without root). The UI shows the hotspot AP with correct SSID, vendor, channel, frequency and security; the packet view shows IP/TCP/HTTP from `open-http.pcap` and `managed-http.pcap`.
- **Live monitor mode (laptop):**
  - The AP list fills within about 5 s across channels 1–13, and the channel graph matches `docs/monitor-test.sh` and `sudo tcpdump -e`.
  - Pin the hotspot's channel. A second device loads `http://neverssl.com`, and the HTTP GET appears with ASCII payload.
- **Live managed mode:** `curl http://neverssl.com` shows the "Ethernet header (managed mode)" layer, then IPv4, TCP and HTTP.
- **Before demo day:** rehearse `docs/DEMO.md` start to finish, with the replay fallback ready.

# Course-notes.pdf — Summary & Reading Guide (for the project)

The PDF has 110 pages. **About 25 matter for the project.** Page numbers are PDF pages.
The frame-format diagrams (Fig. 29, 30, 31, 33) are images, so look at them in the PDF itself.

## 1. What to read, in order

| Priority | Pages | Topic | Why it matters for us |
|---|---|---|---|
| **Must** | 48–49 | 802.11 generalities: BSS / ESS / ad-hoc, SSID, AP | Vocabulary of the Wi-Fi Analyzer (SSID, BSSID = AP MAC) |
| **Must** | 52 + 61 | 802.11b: **13 overlapping channels** (Fig. 6), DSSS channel frequencies | Channel graph and channel ↔ frequency mapping |
| **Must** | 65–67 | **802.11 MAC frame**: Fig. 29 (frame layout), Fig. 30 (Frame Control), type/subtype codes, the 4 addresses | What the sniffer parses byte by byte |
| **Must** | 67–68 | 802.3 Ethernet frame (Fig. 31), **LLC/SNAP encapsulation of IP** (Fig. 33) | Managed mode gives Ethernet frames; monitor mode gives 802.11 + LLC/SNAP before IP |
| **Must** | 69–70 | Wireless sniffing: promiscuous vs **monitor** mode; `airmon-ng`, `airodump-ng` commands | What monitor mode is and how to enable it on Linux |
| Skim | 55 | 802.11 stack (PHY / MAC / LLC / Network) | Mental model of the layers |
| Skim | 65 | CSMA/CA, RTS/CTS, ACK | Explains control frames (ACK, RTS, CTS) you'll see flooding the capture |
| Skim | 71–73 | WEP and its weaknesses | Only for the "security" column: WEP = privacy bit with no WPA/RSN element |
| Skip | 1–47 | Radio physics, source coding (Huffman, LZW), modulation, Fourier, GSM, LTE | Theory; nothing in the project uses it |
| Skip | 56–64 | FHSS/DSSS modulation, PLCP preamble | Done in hardware; libpcap never shows it |
| Skip | 73–110 | SDN, HPC cluster (xCAT, LSF, GPFS), monolith vs microservices, containers (Podman), Kubernetes/OpenShift | Other parts of the course, unrelated to the project |

**Realistic budget:** about 1 hour for the "Must" pages.

## 2. Summary of the project-relevant topics

### 2.1 Wi-Fi network types (p. 48)
- **BSS**: one access point (AP) plus its clients. The AP's MAC address is the **BSSID**.
- **ESS**: several APs sharing the same SSID, connected by a wired "distribution system" (DS).
- **Ad-hoc**: devices talk directly to each other, without an AP.
- **SSID**: the network name, at most 32 bytes. APs advertise it in **beacon frames** (about 10 per second).

### 2.2 Channels and frequencies (p. 52, 61)
- In 2.4 GHz, channel *n* has centre frequency **2407 + 5·n MHz** (ch 1 = 2412, ch 6 = 2437, ch 13 = 2472). Channel 14 (2484) is Japan-only.
- Channels are 5 MHz apart, but a transmission is about 20–22 MHz wide, so each network overlaps roughly ±2 neighbouring channels. That's why the Android app draws each network as a "hill" spanning several channels. Non-overlapping sets: 1/6/11 (the classic set), or 1/5/9/13 in Europe.

### 2.3 The 802.11 MAC frame (p. 65–67), what we parse
Layout: `Frame Control (2) | Duration (2) | Addr1 (6) | Addr2 (6) | Addr3 (6) | Sequence Control (2) | [Addr4 (6)] | Frame Body | FCS (4)`

**Frame Control** (Fig. 30): protocol version, **type** (00 = management, 01 = control, 10 = data), **subtype**, and flags: To DS, From DS, More Fragments, Retry, Power Mgmt, More Data, **Protected** (encrypted), Order.
- Management subtypes we care about: **1000 = beacon**, **0101 = probe response**. Both carry SSID, channel and security info.
- Control frames (ACK, RTS, CTS) are short and mostly noise for us.
- Data frames carry the IP packets.

**The four addresses** (the notes only list them, so this is the missing table). Their meaning depends on To DS / From DS:

| To DS | From DS | Addr1 | Addr2 | Addr3 | Addr4 | Typical case |
|---|---|---|---|---|---|---|
| 0 | 0 | Dest | Source | BSSID | – | Management frames (beacons) |
| 0 | 1 | Dest | BSSID | Source | – | AP → client |
| 1 | 0 | BSSID | Source | Dest | – | Client → AP |
| 1 | 1 | Receiver | Transmitter | Dest | Source | Wireless bridge (mesh) |

"Source and destination MAC" in the requirements means you pick the right address from this table.

### 2.4 Ethernet vs 802.11 and LLC/SNAP (p. 67–68)
- An **Ethernet (802.3)** frame is `Dest MAC (6) | Source MAC (6) | EtherType (2) | payload`. In **managed mode** the Wi-Fi driver hands you frames in this format, so there's no 802.11 header.
- In 802.11 the IP packet is wrapped in **LLC/SNAP** after the MAC header: `AA AA 03 | 00 00 00 | EtherType (2)`. EtherType `0x0800` = IPv4, `0x0806` = ARP (Fig. 33).

### 2.5 Monitor mode and sniffing (p. 69–70)
- **Promiscuous mode** shows all packets on the network you're connected to.
- **Monitor mode** shows **every frame in radio range**, including beacons from networks you're not connected to. That's why the requirements insist on it for the Wi-Fi Analyzer.
- The notes enable it with `airmon-ng start wlp2s0`, stop interfering services with `airmon-ng check kill`, and list networks with `airodump-ng`. Our app does what `airodump-ng` does, only with a GUI.
- The WPA2-cracking walkthrough (handshake capture, `aireplay-ng` deauth) is **out of scope**. Don't run it on networks you don't own.

### 2.6 Security (p. 71–73)
- **WEP** uses the RC4 stream cipher with a 24-bit IV. IVs repeat quickly, so keys can be recovered. It's broken.
- **The WPA/WPA2 section in the notes is empty** (only the heading exists). See §3 for what to read instead.

## 3. Gaps: topics the project needs that the notes don't cover

| Topic | What you need from it | Where to read |
|---|---|---|
| **Radiotap header** | Prepended to every frame in monitor mode. Gives signal strength (dBm), frequency and flags (including "FCS present at end"). Fields are little-endian and aligned. | radiotap.org: the "Radiotap header" page plus the "Defined fields" list (Flags, Channel, Antenna signal) |
| **Beacon / probe response body** | Fixed part: timestamp (8), beacon interval (2), capability info (2, contains the **Privacy** bit). Then a list of **Information Elements** `tag (1) | length (1) | value`: tag 0 = SSID, 3 = DS Parameter (channel), 48 = RSN (WPA2/WPA3), 221 = vendor (WPA1 if OUI 00:50:F2 type 1) | Matthew Gast, *802.11 Wireless Networks: The Definitive Guide* (the book the notes cite), ch. 4 "802.11 Framing in Detail" |
| **WPA2 / WPA3 detection** | RSN element: group cipher, pairwise ciphers, AKM suites. AKM 2 = PSK (WPA2-Personal), 1 = 802.1X (Enterprise), 8 = SAE (WPA3) | Gast ch. 7–8, or the Wireshark wiki page on 802.11 / RSN |
| **IPv4 header** | All fields: version, IHL, DSCP/ECN, total length, ID, flags/fragment offset, TTL, protocol (6 = TCP), checksum, source/destination, options | RFC 791, section 3.1 (one page with a diagram) |
| **TCP header** | Ports, sequence/ack numbers, data offset, flags (CWR ECE URG ACK PSH RST SYN FIN), window, checksum, urgent pointer, options | RFC 9293, section 3.1 |
| **libpcap API** | `pcap_findalldevs`, `pcap_create`, `pcap_set_rfmon`, `pcap_activate`, `pcap_datalink`, `pcap_next_ex`, `pcap_close` | `man pcap` and the per-function man pages; tcpdump.org "Link-layer header types" (DLT 127 vs DLT 1) |
| **Byte order** | Radiotap/802.11 are little-endian; IP/TCP are big-endian ("network order") | Any short "endianness" explainer |

**Best learning shortcut:** open one of your own captures in **Wireshark** and click through a beacon and an HTTP packet. Wireshark shows each field next to its bytes, which is exactly what our parser must reproduce.

## 4. One-line summaries of the rest of the course (not needed for the project)
- **Radio basics (p. 1–10):** electromagnetic spectrum, radio waves, microwaves, infrared, Bluetooth (2.4 GHz, frequency hopping, piconets of up to 8 devices).
- **Digital communication (p. 11–30):** source → source encoder → channel encoder → channel. Sampling/quantization, Huffman and LZW compression, ASK/FSK/PSK/QAM modulation, Fourier series and the transform.
- **GSM / LTE (p. 31–47):** cellular architecture, logical vs physical channels, call setup, SIM-based authentication, LTE EPC and protocol layers.
- **SDN (p. 73–85):** control plane separated from data plane, central controller, OpenFlow, use cases and tools.
- **HPC cluster (p. 86–90):** xCAT node provisioning, LSF job scheduler, GPFS parallel file system.
- **Applications and containers (p. 90–110):** monolith vs microservices, Podman/Docker (run, volumes, ports, images, registries, Dockerfile), Kubernetes/OpenShift resources (pods, services, routes) and a local OpenShift install.

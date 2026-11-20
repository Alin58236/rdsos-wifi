# Session Handoff — 2026-11-20

Context from the first Claude Code session (on the Windows desktop), so the work can continue on the laptop.
**New session: read this file, `CLAUDE.md`, `docs/requirements.md` and `docs/course-notes-summary.md` first.**

## 1. Who does what
- **The user writes all the code.** Claude guides: explains concepts, suggests structure, points to reading, reviews code, answers questions. Claude does not implement backend/frontend files unless asked for a specific piece. Short illustrative snippets in explanations are fine; docs and notes are fine.
- The user wants concise, direct answers and step-by-step guidance for machine setup.

## 2. Project decisions so far
- Course: RDSOS (Retele Dinamice si Sisteme de Operare Specializate), master's year 2, semester 1.
- Projects **1 (Wi-Fi Analyzer) and 2 (packet sniffer) are built as one app**. The goal is to do them well, with real monitor mode and manual parsing, so **Project 3 is not needed**. Project 3 is only required if monitor mode or manual management-frame parsing is skipped.
- Stack: **Spring Boot backend (Java 25)** with hand-written byte parsers, and a **Vite + React + TS + shadcn/ui** frontend on localhost. Next.js was rejected: it's a local tool, so SSR/SEO add nothing. The website launch checklist from the user's global instructions doesn't apply.
- Capture: **Java FFM API (`java.lang.foreign`) calling libpcap directly**, with no capture library (option A). Rejected: pcap4j (stale; its built-in parsers would cost grade) and raw `AF_PACKET` via FFM (possible later as an extra). *Option A was the recommendation and the user hasn't objected, but hasn't confirmed it explicitly either. Confirm before the backend scaffold.*
- Build tool: Gradle (wrapper). Assumed, not discussed in depth.
- Full architecture, parsing gotchas and security rules are in `CLAUDE.md`.

## 3. Machines
- **Desktop** (ASRock B650, Windows 10): no Wi-Fi adapter at all. Only useful for parser work against `.pcap` fixtures.
- **Laptop** (Asus TUF FX505DT, Windows, AMD Ryzen + GTX 1650): the dev and capture machine. Plan: **dual boot Ubuntu 24.04 LTS**.
- Laptop Wi-Fi card: **Realtek RTL8822CE** (802.11ac, PCIe). Linux driver: in-kernel `rtw88_8822ce`. Monitor mode almost certainly shows up in `iw list`; passive capture of other networks' beacons is **not yet verified**. Reports for rtw88 are mixed.
- Fallback if the card fails the test: a USB adapter with chipset **MT7612U / MT7921AU** (e.g. Alfa AWUS036ACM) or **AR9271** (Alfa AWUS036NHA, or TP-Link TL-WN722N **v1 only**). Only 2.4 GHz is required (13 channels).

## 4. Where we stopped: dual boot, step 2 (live USB monitor-mode test)
Done: identified the Wi-Fi card (RTL8822CE).

**Still needed from the user (laptop, Windows, admin PowerShell):**
```powershell
manage-bde -status C:                   # BitLocker on? If yes, back up the recovery key first
Get-Volume C | Select SizeRemaining, Size
Get-Disk | Select Number, FriendlyName, PartitionStyle, Size
```
Also: is a USB stick of 8 GB or more available to wipe?

**Live USB test, before any partitioning:**
1. Download the Ubuntu 24.04 LTS Desktop ISO and write it with Rufus (GPT, UEFI).
2. In Windows, turn off Fast Startup (Power Options → "Choose what the power buttons do").
3. Boot the USB (tap Esc, or F8 on some TUF models, at power-on), keep Secure Boot on, choose "Try Ubuntu".
4. Run:
```bash
iw dev
lspci -k | grep -iA3 network             # expect: Kernel driver in use: rtw88_8822ce
which tcpdump || (sudo apt update && sudo apt install -y tcpdump)   # needs internet: connect to Wi-Fi FIRST, before monitor mode
IF=wlo1                                  # use the real name from `iw dev`
sudo nmcli dev set $IF managed no
sudo ip link set $IF down && sudo iw dev $IF set type monitor && sudo ip link set $IF up
iw dev $IF info                          # expect: type monitor
for ch in 1 6 11; do sudo iw dev $IF set channel $ch; echo "== ch $ch =="; sudo timeout 5 tcpdump -i $IF -e -c 10 'type mgt subtype beacon'; done
```
- **Pass:** beacons from **several different SSIDs** with `-NNdBm signal` and `NNNN MHz`. Go ahead with the dual boot.
- **Fail:** no beacons, only own network, or `set type monitor` errors. Buy a USB adapter; still dual boot.
- `iw list` showing `monitor` is **not** proof on its own: Linux's Wi-Fi layer (mac80211) lists monitor mode for almost every card. Only the beacon capture above counts.
- When finished, **reboot**. That puts the card back in normal mode; nothing on disk changed.
- Paste the output of the final loop to Claude either way.

## 5. Remaining dual-boot plan (after the test passes)
1. Back up data. If BitLocker is on, save the recovery key (or suspend BitLocker).
2. Shrink C: in Windows Disk Management by about 60–80 GB (unallocated).
3. Boot the USB → Install Ubuntu → "Install alongside Windows", or manual: an ext4 `/` in the free space, reusing the existing EFI partition.
4. GTX 1650: if the installer or first boot shows a black screen, boot with `nomodeset`, then install the proprietary NVIDIA driver ("Additional Drivers").
5. If Wi-Fi drops out on Linux: try the `rtw88_pci disable_aspm=1` / `rtw88_core disable_lps_deep=y` module options.
6. After install: `sudo apt install -y git iw tcpdump wireshark libpcap-dev`, a JDK 25, and Node LTS. Then clone the repo.

## 6. Project roadmap (the user implements; Claude guides)
0. Hardware check and test captures from **your own hotspot only**: beacons; an open network with plain HTTP (monitor mode); HTTP in managed mode; WPA2 data. Save them as `.pcap` fixtures.
1. Capture layer: FFM bindings to libpcap, a capture thread, a ring buffer, and a hand-written `.pcap` file reader for tests.
2. Management-frame parsers (TDD): Radiotap, 802.11 header, beacon information elements (SSID, channel, RSN/WPA security), OUI vendor lookup.
3. Wi-Fi Analyzer backend: AP registry, channel hopper, monitor/managed mode switching.
4. Sniffer parsers (TDD): 802.11 data, LLC/SNAP, Ethernet, IPv4, TCP, HTTP (port 80).
5. API: REST controls, WebSocket batches about every 200 ms.
6. Frontend: AP list with signal-colour icons, channel graph (SVG), packet list with layer tree and hex dump.
7. Demo script for both modes.

## 7. Files in the repo
- `CLAUDE.md`: project guidance for Claude Code (architecture, commands, gotchas).
- `docs/requirements.md`: the course spec, verbatim (the grading rubric).
- `docs/course-notes-summary.md`: what to read in `Course-notes.pdf` (about 25 of 110 pages), topic summaries, and external sources for the gaps (Radiotap, information elements, WPA2, IPv4, TCP, libpcap).
- `Course-notes.pdf`: course material.

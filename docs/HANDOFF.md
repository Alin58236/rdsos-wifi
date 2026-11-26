# Session Handoff — 2026-11-25

Context from the Claude Code sessions so far: session 1 on the Windows desktop; session 2 on the laptop (Windows side), which prepared the no-USB live boot; session 3 on the laptop's installed Ubuntu, which fixed 2.4 GHz monitor mode (§4).
**New session: read this file, `CLAUDE.md`, `docs/requirements.md` and `docs/course-notes-summary.md` first.**

## 1. Who does what
- **Split by grade weight (since session 3, because of the 7-day deadline).** The user writes the graded core: FFM capture, the `.pcap` reader, `CaptureService`, all parsers, and the AP/security logic. Claude writes the plumbing (Spring config, `iface/`, REST/WebSocket, the whole React frontend) and reviews the user's code. Order since 2026-11-22: the whole backend first, then the frontend; target Thursday night. Details are in `CLAUDE.md` → Working mode, and the schedule is in `docs/PLAN.md`.
- `CLAUDE.md` and this file stay **tracked in git**: they are how the desktop and laptop sessions stay in sync.
- The user wants concise, direct answers and step-by-step guidance for machine setup.

## 2. Project decisions so far
- Course: RDSOS (Retele Dinamice si Sisteme de Operare Specializate), master's year 2, semester 1.
- Projects **1 (Wi-Fi Analyzer) and 2 (packet sniffer) are built as one app**. The goal is to do them well, with real monitor mode and manual parsing, so **Project 3 is not needed**. Project 3 is only required if monitor mode or manual management-frame parsing is skipped.
- Stack: **Spring Boot backend (Java 25)** with hand-written byte parsers, and a **Vite + React + TS + shadcn/ui** frontend on localhost. Next.js was rejected: it's a local tool, so SSR/SEO add nothing. The website launch checklist from the user's global instructions doesn't apply.
- Capture: **Java FFM API (`java.lang.foreign`) calling libpcap directly**, with no capture library (option A). Rejected: pcap4j (stale; its built-in parsers would cost grade) and raw `AF_PACKET` via FFM (possible later as an extra). **Confirmed:** the user is going ahead with the FFM bindings (next session, §5).
- Build tool: **Maven** with the Maven wrapper (`./mvnw`), the user's choice in session 3. FFM is final in Java 25 (no preview flag), but run with `--enable-native-access=ALL-UNNAMED` to avoid the restricted-method warning.
- Full architecture, parsing gotchas and security rules are in `CLAUDE.md`.

## 3. Machines
- **Desktop** (ASRock B650, Windows 10): no Wi-Fi adapter at all. Only useful for parser work against `.pcap` fixtures.
- **Laptop** (Asus TUF **FX505DU** per `Win32_ComputerSystem`, earlier noted as FX505DT; Windows 10 Pro, AMD Ryzen + GTX 1650): the dev and capture machine. **Dual boot Ubuntu 24.04.5 installed** (kernel 7.0.0-43, ext4 `/` on `nvme0n1p6`).
- Laptop disk: one 512 GB NVMe (Intel SSDPEKNW512G8), GPT, **no BitLocker** on C: or D: (checked with `manage-bde`: Fully Decrypted, no key protectors).
- Laptop Wi-Fi card: **Realtek RTL8822CE** (802.11ac, PCIe). Linux driver: in-kernel `rtw88_8822ce`, interface `wlp4s0`. **Monitor mode verified on 2.4 GHz and 5 GHz**, with a module reload first (§4).
- Backup only (no longer needed): a USB adapter with chipset **MT7612U / MT7921AU** (e.g. Alfa AWUS036ACM) or **AR9271** (Alfa AWUS036NHA, or TP-Link TL-WN722N **v1 only**). Only 2.4 GHz is required (13 channels).

## 4. Monitor-mode tests: SOLVED (both bands work after a driver reload)

### Session 3 (installed Ubuntu, kernel 7.0.0-43, in-tree `rtw88`): the fix
**Cause:** the boot-time managed connection (5 GHz ch 36, 80 MHz) leaves the driver unable to tune to 2.4 GHz. `iw` reports the new channel, but the radio stays on 5 GHz. Hopping *within* 2.4 GHz was never the problem.

**Fix:** reload the driver module before entering monitor mode. After the reload, everything works:
```bash
sudo modprobe -r rtw88_8822ce && sudo modprobe rtw88_8822ce   # wait ~1-2 s for wlp4s0 to reappear
sudo nmcli dev set wlp4s0 managed no
sudo ip link set wlp4s0 down && sudo iw dev wlp4s0 set type monitor && sudo ip link set wlp4s0 up
sudo iw dev wlp4s0 set channel 1
```

**Evidence** (tcpdump beacon counts; the "beacon `CH:`" value is IE 3 from the beacon itself, i.e. where the radio really is):
| Test | Result |
|---|---|
| No reload: `set channel` 1/6/11 with the interface up, or a managed→monitor type toggle | 0 beacons on every channel (5 GHz ch 36 control: 82 beacons, 3 BSSIDs) |
| No reload: down → `set channel`/`set freq` → up | `EBUSY (-16)`: channels can't be set while a monitor interface is down |
| After a reload: ch 1 / 6 / 11 | 151 / 49 / 22 beacons, beacon `CH:` matches |
| After a reload, plain `set channel` hops | all work, including 2.4 → 5 GHz → 2.4 GHz |
| **Hopper simulation:** ch 1–13 at 250 ms for 20 s | 204 beacons from 21 BSSIDs; beacon `CH:` 1, 2, 4, 5, 6, 10, 11, 13; Radiotap frequencies on all channels except 2422 and 2447 MHz (channels 3 and 8: likely no APs there, and neither is directly next to a busy channel) |

- The hopper simulation is saved as `docs/monitor-test.sh` (takes an optional interface and driver module, and prints counts only).
- `/etc/modprobe.d/rtw88.conf` (`rtw88_core disable_lps_deep=y`, `rtw88_pci disable_aspm=y`) is installed; on its own, it does **not** fix 2.4 GHz. It's kept because it's harmless.
- Parser notes from the captures: beacons from neighbouring channels leak in, so take the channel from IE 3. This card sends three Radiotap dBm antenna signal fields (combined, antenna 0, antenna 1); use the combined one.

### Session 2 (live USB, kernel 7.0.0-31 HWE): first attempt
**Result:**
- **5 GHz works:** monitor mode captures beacons + data frames (2 SSIDs on ch 36). With `rtw88_core disable_lps_deep=y` + `rtw88_pci disable_aspm=y` reloaded, channel switching works (36 → 149, Radiotap shows 5 GHz ch 149).
- **2.4 GHz is broken: the radio doesn't retune within 2.4 GHz in monitor mode.** Only the last *associated* 2.4 GHz channel receives. Associated to own network on ch 10, then monitor and hop 11 → 9 → 1 → 10 gave 0, 0, 0, 3 packets. Ch 10 showed real 2.4 GHz traffic (1 Mb/s 11b beacons, probe requests, RTS). Before any 2.4 GHz association, "ch 1" showed **ch 36 traffic labelled 2412 MHz**: `iw` reports the new channel but the hardware stays where it was.
- No change from: Bluetooth off (rfkill + bluetooth service stopped), `iw reg set RO`, NetworkManager/wpa_supplicant stopped.
- **Why it matters:** `requirements.md` requires the graph of the **13 Wi-Fi channels**, i.e. 2.4 GHz. 5 GHz alone does not satisfy Project 1. An OS scan API is not an allowed fallback.
- A newer kernel is not the fix: the ISO already runs the HWE 7.0 kernel, and mainline kernels are unsigned (Secure Boot).

The setup below is kept for reference until the cleanup in §5.

### Live boot setup (session 2, no USB)
No USB stick was available, so the Ubuntu ISO was put on a FAT32 partition on the internal SSD, which UEFI boots like a stick. A VM/WSL2 can't be used: they can't pass the built-in PCIe Wi-Fi card through.

**Done in session 2 (laptop, Windows, admin):**
- Global Claude Code setup from `docs/claude-setup/` installed (CLAUDE.md, web guidelines, skills, settings merged with the existing `theme: dark`; vercel + figma plugins installed).
- **Disk repartitioned** (D: shrunk 250 → 122 GB, data untouched). Layout by offset:

  | Start | Partition | Size | Role |
  |---|---|---|---|
  | 0 | EFI (System), FAT32 | 96 MB (67.8 MB free) | Shared boot partition; Ubuntu needs only ~6 MB here |
  | 0.10 GB | MSR (Reserved) | 16 MB | Windows |
  | 0.11 GB | **D:** NTFS | 122 GB | Data |
  | 122.11 GB | **U: `UBUNTU`** FAT32 | 8 GB | Live/installer copy of the ISO. Temporary |
  | 130.11 GB | **unallocated** | 120 GB | Reserved for the Ubuntu install (ext4 `/`) |
  | 250.11 GB | **C:** NTFS | 227 GB | Windows |
- ISO `ubuntu-24.04.5.1-desktop-amd64.iso` downloaded to `C:\Users\Stoia\Downloads`, SHA256 verified against releases.ubuntu.com, contents copied to U: (1020 files, 6.24 GB; largest file 2.08 GB, so FAT32's 4 GB limit is fine).
- **Fast Startup off** (`HKLM\...\Session Manager\Power\HiberbootEnabled = 0`).
- **UEFI boot entry "Ubuntu Live"** `{04a2b5f1-be2d-11ee-94b0-c915f0101598}` → `partition=U:` `\EFI\BOOT\BOOTX64.EFI`, added **last** in `{fwbootmgr}` displayorder (Windows stays default). BCD backup before the change: `C:\Users\Stoia\Desktop\bcd-backup-2026-11-20.bcd` (restore with `bcdedit /import`).

**How the test was run:**
1. Shut down fully, power on, tap **Esc** (or F8; or BIOS F2 → Boot) → **Ubuntu Live** → GRUB "Try or Install Ubuntu" → "Try Ubuntu". Secure Boot stays on. Black screen → `e` in GRUB, add `nomodeset` after `quiet splash`, F10.
2. Connect to Wi-Fi **first** (needed for `apt`), then Terminal:
```bash
iw dev
lspci -k | grep -iA3 network             # expect: Kernel driver in use: rtw88_8822ce
sudo apt update && sudo apt install -y tcpdump
IF=wlo1                                  # use the real name from `iw dev`
sudo nmcli dev set $IF managed no
sudo ip link set $IF down && sudo iw dev $IF set type monitor && sudo ip link set $IF up
iw dev $IF info                          # expect: type monitor
for ch in 1 6 11; do sudo iw dev $IF set channel $ch; echo "== ch $ch =="; sudo timeout 5 tcpdump -i $IF -e -c 10 'type mgt subtype beacon'; done 2>&1 | tee ~/wifi-test.txt
```
3. Copy `~/wifi-test.txt` to the 122 GB volume (D:) via the Files app, or photograph the screen. Bring it to Claude.
- **Pass:** beacons from **several different SSIDs** with `-NNdBm signal` and `NNNN MHz`.
- **Fail:** no beacons, only own network, or `set type monitor` errors. Buy a USB adapter; still dual boot.
- `iw list` showing `monitor` is **not** proof on its own: Linux's Wi-Fi layer (mac80211) lists monitor mode for almost every card. Only the beacon capture above counts.
- The live session runs from RAM; nothing installed there persists.

## 5. Status and next steps (2026-11-25, Wednesday evening)

**The app is feature-complete for `docs/requirements.md`.** 193 backend tests pass; the frontend lints and builds. Everything is committed except the doc updates of this session.

Done (who wrote it: U = user, C = Claude, as agreed in `CLAUDE.md`; the user asked Claude to write some graded parts when stuck):
- Capture: `LibPcap` + `PcapLiveSource` (FFM, C at the user's request), `PcapFileReader` (U), `CaptureService` with the live loop and paced replay (U, reviewed).
- Parsers (U, tests by C): Radiotap, 802.11 (management, data, QoS, control), IEs, beacon, security, Ethernet, LLC/SNAP, HTTP payload. IPv4 and TCP: the user started them, Claude finished them. `PacketDecoder` (U, step 1 corrected by C).
- `PacketStore`, beacon parsing moved into the decoder, `processFrame` wiring, `PacketViewMapper` (C at the user's request).
- API: packet endpoint with 404, packets in the WebSocket (≤ 500 per tick, the rest in the next tick). `status.linkType` removed (user's decision).
- Frontend: controls, channel graph, AP table, packet list, layer tree, hex dump with layer highlighting.
- **Verified live on the laptop:** the AP list in monitor mode, and the sniffer in managed mode with `curl http://neverssl.com`.

Dropped from the scope (user's decision, 2026-11-25): UI packet filters, UDP, IPv6, checksum verification, decoding of IP/TCP options (shown as raw hex). Not implemented: 4-address frames and the 802.11 Order bit; `status.dropped` is always 0.

Next (Thursday 2026-11-26):
1. **Monitor-mode sniffer on an open 2.4 GHz network** (router guest network without a password, or an Android hotspot; an iPhone hotspot always has a password), with a second device loading `http://neverssl.com` and the channel pinned. This is the one requirement not yet seen live. Steps in `docs/DEMO.md` §3.
2. Record the backup capture outside the repo (`docs/DEMO.md` §0).
3. Rehearse with `docs/DEMO.md`. Study `docs/FLOW.md` (the whole data flow in Romanian, with sources for every bit layout and answers to likely questions).

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
- `docs/claude-setup/`: the user's global Claude Code setup (global CLAUDE.md, settings, skills) with install steps. Installed on the laptop's Windows side in session 2 and on its Ubuntu in session 3.
- `docs/info_for_project.md`: the user's study notes, in Romanian with English terms. Project-relevant course content (p. 1–73) plus implementation notes: §15 which course tools to use, §16 libpcap capture and loop design.
- `docs/monitor-test.sh`: monitor-mode hopper test (driver reload, channels 1–13 at 250 ms, beacon counts per real channel).
- `docs/API.md`: the REST/WebSocket contract between backend and frontend.
- `docs/PLAN.md`: the original day plan, the technical reference per parser, and the scope cuts.
- `docs/CAPTURES.md`: how the `.pcap` fixtures were recorded.
- `docs/DEMO.md`: the demo script, plan B (replay) and troubleshooting.
- `docs/FLOW.md`: the whole data flow from the card to the browser, every parser check, and where each bit layout comes from (Romanian, for the user).

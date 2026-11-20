# Session Handoff — 2026-11-20

Context from the Claude Code sessions so far: session 1 on the Windows desktop, session 2 on the laptop (Windows side), which prepared the no-USB live boot.
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
- **Laptop** (Asus TUF **FX505DU** per `Win32_ComputerSystem`, earlier noted as FX505DT; Windows 10 Pro, AMD Ryzen + GTX 1650): the dev and capture machine. Plan: **dual boot Ubuntu 24.04 LTS**.
- Laptop disk: one 512 GB NVMe (Intel SSDPEKNW512G8), GPT, **no BitLocker** on C: or D: (checked with `manage-bde`: Fully Decrypted, no key protectors).
- Laptop Wi-Fi card: **Realtek RTL8822CE** (802.11ac, PCIe). Linux driver: in-kernel `rtw88_8822ce`. Monitor mode almost certainly shows up in `iw list`; passive capture of other networks' beacons is **not yet verified**. Reports for rtw88 are mixed.
- Fallback if the card fails the test: a USB adapter with chipset **MT7612U / MT7921AU** (e.g. Alfa AWUS036ACM) or **AR9271** (Alfa AWUS036NHA, or TP-Link TL-WN722N **v1 only**). Only 2.4 GHz is required (13 channels).

## 4. Live monitor-mode test: DONE (5 GHz works, 2.4 GHz broken)

**Result (live session, in-tree `rtw88`, kernel 7.0.0-31 HWE):**
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

## 5. Next: install the dual boot, then fix 2.4 GHz

**2.4 GHz plan, in parallel with the install:**
- **Order a 2.4 GHz monitor-mode USB adapter now** as insurance: **AR9271** (Alfa AWUS036NHA, or TP-Link TL-WN722N **v1 only**; v2/v3 are Realtek and don't work). An MT7612U (Alfa AWUS036ACM) also works and adds 5 GHz.
- On the installed Ubuntu, cheapest first:
  1. Keep the module options permanently in `/etc/modprobe.d/rtw88.conf` (`options rtw88_core disable_lps_deep=y`, `options rtw88_pci disable_aspm=y`).
  2. Force a full reconfiguration per hop: `ip link set $IF down; iw dev $IF set channel N; ip link set $IF up`, and try `iw dev $IF set freq 2412` instead of `set channel`.
  3. Out-of-tree **`lwfinger/rtw88`** (newer rtw88 code, DKMS). With Secure Boot on, the module must be signed with an enrolled MOK key (DKMS asks for a password at install and MokManager asks for it once on the next boot).
  4. Otherwise use the USB adapter. The app's design is unchanged: same libpcap/Radiotap path, only a different interface name.

**Install steps:**
1. From the same live session: "Install Ubuntu 24.04" → **Manual installation** (never "Erase disk") → the **120 GB free space** → ext4, mount point `/` (no swap partition; Ubuntu uses a swap file). Leave the existing EFI partition unformatted. Boot loader device: `/dev/nvme0n1`.
2. After install, GRUB is the default boot loader and shows a menu to choose Ubuntu or Windows Boot Manager.
3. GTX 1650: if first boot shows a black screen, boot with `nomodeset`, then install the proprietary NVIDIA driver ("Additional Drivers").
4. If Wi-Fi drops out on Linux: try the `rtw88_pci disable_aspm=1` / `rtw88_core disable_lps_deep=y` module options.
5. After install: `sudo apt install -y git iw tcpdump wireshark libpcap-dev`, a JDK 25, Node LTS, IntelliJ, VS Code and Claude Code (then install `docs/claude-setup/` there too). Clone the repo.
6. Cleanup (from Windows, admin): `bcdedit /delete {04a2b5f1-be2d-11ee-94b0-c915f0101598}`, delete the `UBUNTU` partition (U:), and give the 8 GB back to D:.

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
- `docs/claude-setup/`: the user's global Claude Code setup (global CLAUDE.md, settings, skills) with install steps. Installed on the laptop's Windows side in session 2; install it again on the laptop's Ubuntu once that exists (see its README).

---
name: lgtv
description: Diagnose and fix downloaded video files so they play on the user's LG QNED TV via its built-in USB media player (external SSD). Probes audio/video/subtitles with ffprobe, flags formats the TV can't play (DTS, TrueHD, Dolby Vision profile 5), and converts them with ffmpeg. Use when the user says /lgtv, or asks why a movie/episode has no sound or won't play on the TV.
---

# LG TV playback doctor

The user plays files from an external SSD (usually `F:\Smart TV`) in the LG QNED's built-in USB media player.
Goal: find out why a file won't play (no sound / "file could not be recognized" / wrong colours) and fix it with the least quality loss.

## Known facts about this TV (confirmed in practice)

| Track | Status on this TV | Fix |
|---|---|---|
| Audio **DTS / DTS-HD MA / DTS:X** | NOT supported: video plays, **no sound** | Convert audio only to E-AC3 (video copied) |
| Audio **TrueHD / TrueHD Atmos** | NOT supported: **no sound** | Convert to E-AC3, or keep an existing AC3/E-AC3 track and drop TrueHD |
| Audio AC3, E-AC3 (incl. DD+ Atmos), AAC | OK | Nothing |
| Video **Dolby Vision profile 5** (`dv_profile=5`, `dv_bl_signal_compatibility_id=0`, color_transfer `unknown`) | NOT playable: "file could not be recognized". The TV refuses Dolby Vision, and P5 has no HDR10 fallback (base layer is in Dolby's own colour format, so it shows green/purple if stripped) | Full re-encode to HDR10 with libplacebo (see below). Remuxing to MP4 does NOT help (tested) |
| Video DV **profile 7** (compat id 6) or **profile 8.1** (compat id 1) | OK: TV ignores DV and plays the HDR10 base layer | Nothing, copy video |
| Video DV profile 8.4 (HLG base) | Should play as HLG | Nothing |
| Video HEVC Main10 HDR10 / H.264 | OK | Nothing |

Unknown codec not in this table (FLAC, PCM, Opus, AV1, VC-1, MPEG-2, ASS/PGS subs): say it's unverified on this TV, suggest a short test clip rather than guessing.

## Step 1: Diagnose

For each video file the user points at (or every `.mkv/.mp4/.m2ts/.ts` in a folder they name):

```bash
ffprobe -v error -show_entries format=duration,size:stream=index,codec_type,codec_name,profile,pix_fmt,width,height,color_transfer,color_primaries,channels,channel_layout:stream_tags=language,title:stream_side_data=dv_profile,dv_bl_signal_compatibility_id -of compact "<file>"
```

Report a short table per file: video (codec, resolution, HDR type / DV profile), each audio track (codec, channels, language), verdict (OK / no sound / won't play) and the fix. Check whether a compatible audio track already exists before proposing a conversion; often the fix is just dropping the bad track.

## Step 2: Fix (always confirm with the user first; write NEW files next to the originals)

Disk space: check `df -h` before converting; outputs are roughly source-sized.

### A. Audio only (DTS / TrueHD): fast, video untouched (~10-20 min for a 4K movie, disk-bound)
```bash
ffmpeg -hide_banner -loglevel error -stats -i "in.mkv" -map 0 -c copy -c:a eac3 -b:a 1024k -ac 6 "out.mkv"
```
- `-ac 6` because ffmpeg's E-AC3 encoder tops out at 5.1 (7.1 sources get downmixed).
- If the file has several audio tracks, map only the ones wanted (e.g. `-map 0:v -map 0:a:0 -map 0:s?`).
- If a matching external `.srt` sits next to the source, copy it with the new basename so the TV auto-loads it.
- Name the output with the audio swapped in the name (e.g. `DTS-HD.MA.5.1` -> `DDP.5.1`).

### B. Dolby Vision profile 5 -> HDR10: slow re-encode (~2x realtime per file on this PC)
Requirements on this PC:
- `libplacebo` exists only in the **full** gyan build: `/f/Smart TV/ffmpeg-full/ffmpeg-9.0.2-full_build/bin/ffmpeg.exe` (the Chocolatey `ffmpeg` is the essentials build, no libplacebo). If missing, ask before re-downloading `https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-full.7z`, verify against its `.sha256`, extract with `"/c/Program Files (x86)/7-Zip/7z.exe"` (Windows tar can't do LZMA).
- GPU: RTX 4070 SUPER. The full build's `hevc_nvenc` needs NVIDIA driver >= 610; at driver 591.x it fails, so **pipe**: full build does libplacebo, the Chocolatey `ffmpeg` (works with the current driver) does NVENC.
- Colour tags must be set as **input options on the pipe**; output-side `-color_trc` leaves the MKV container tagged "unspecified".
- CPU decode only (`-hwaccel vulkan` into rawvideo fails).

Use the bundled script (resumable, writes `*.part.mkv`, verifies duration, PQ transfer and audio before finalising):
```bash
bash ~/.claude/skills/lgtv/scripts/dv5_to_hdr10.sh "ep1.mkv" "ep2.mkv"            # keeps originals
DELETE_SOURCE=1 bash ~/.claude/skills/lgtv/scripts/dv5_to_hdr10.sh "ep1.mkv"     # only if user asked to delete
```
The external SSD (Disk 2) intermittently drops off USB during long writes (Windows System log: `disk` event 153 "IO operation ... was retried", device re-enumerated); every encode/log write to F: then fails at once (`No such device or address`, muxer `Invalid argument`). If a run fails, check `Get-WinEvent` System log for disk 153 at that time, retry the episode, and point the user at USB selective suspend / rear port / cable. Keep logs on C: (scratchpad) so they survive a drop. After any batch, list the folder and treat a leftover `*.part.mkv` as a failed run; prefer one job at a time when the user is away. Run long jobs with `run_in_background`.

Before a full batch, make a 2-minute test clip (add `-ss 600 -t 120` to both inputs) for the user to check on the TV.

## Step 3: Verify and report
- `ffprobe` the output: audio codec now eac3, or video `color_transfer=smpte2084`, `color_primaries=bt2020`, no `dv_profile`; duration matches source within ~2 s.
- Never delete originals unless the user explicitly asked, and only after verification passes.
- Tell the user which files are ready, and what to test on the TV.

## Gotchas learned
- The LG thumbnail cache folder `TN` on the SSD is not media; ignore it.
- Windows may block `shutdown /a`; don't try to work around the user's shutdown timers. Warn if a job won't finish in time.
- Filenames contain non-ASCII characters (e.g. `Marnûkh`); always quote paths and use globs in bash.

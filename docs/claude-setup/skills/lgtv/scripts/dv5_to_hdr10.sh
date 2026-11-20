#!/usr/bin/env bash
# Convert Dolby Vision profile 5 files to HDR10 so the LG TV can play them.
# Usage: dv5_to_hdr10.sh <source>...        Env: DELETE_SOURCE=1 deletes the source after verification.
# Resumable: finished outputs are skipped, unfinished *.part.mkv are overwritten.
FF_FULL="${FF_FULL:-/f/Smart TV/ffmpeg-full/ffmpeg-9.0.2-full_build/bin/ffmpeg.exe}"
FF_ENC="${FF_ENC:-ffmpeg}"
VF="libplacebo=colorspace=bt2020nc:color_primaries=bt2020:color_trc=smpte2084:range=tv:format=yuv420p10le"

[ -x "$FF_FULL" ] || { echo "Full ffmpeg build (with libplacebo) not found: $FF_FULL"; exit 1; }

for SRC in "$@"; do
  [ -f "$SRC" ] || { echo "[skip] missing: $SRC"; continue; }
  base="${SRC%.*}"
  OUT="${base/DoVi/HDR10}"
  [ "$OUT" = "$base" ] && OUT="${base/.DV./.HDR10.}"
  [ "$OUT" = "$base" ] && OUT="$base.HDR10"
  OUT="$OUT.mkv"
  TMP="${OUT%.mkv}.part.mkv"
  [ -f "$OUT" ] && { echo "[skip] already done: $OUT"; continue; }
  echo "[start] $(date +%T) $SRC"

  "$FF_FULL" -hide_banner -loglevel error -init_hw_device vulkan -i "$SRC" -map 0:v:0 -vf "$VF" -c:v rawvideo -f nut - |
  "$FF_ENC" -hide_banner -loglevel error -y \
    -color_primaries bt2020 -color_trc smpte2084 -colorspace bt2020nc -color_range tv -i - \
    -i "$SRC" -map 0:v -map 1:a -map 1:s? -map_metadata 1 -map_chapters 1 \
    -c:v hevc_nvenc -preset p5 -tune hq -profile:v main10 -pix_fmt p010le \
    -rc vbr -cq 19 -b:v 25M -maxrate 50M -bufsize 100M \
    -c:a copy -c:s copy "$TMP"
  rc=("${PIPESTATUS[@]}")

  src_dur=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$SRC")
  out_dur=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$TMP" 2>/dev/null)
  trc=$(ffprobe -v error -select_streams v:0 -show_entries stream=color_transfer -of csv=p=0 "$TMP" 2>/dev/null)
  acount=$(ffprobe -v error -select_streams a -show_entries stream=index -of csv=p=0 "$TMP" 2>/dev/null | wc -l)
  dur_ok=$(awk -v a="$src_dur" -v b="${out_dur:-0}" 'BEGIN{d=a-b; if(d<0)d=-d; print (d<2)?1:0}')

  if [ "${rc[0]}" = 0 ] && [ "${rc[1]}" = 0 ] && [ "$dur_ok" = 1 ] && [ "$trc" = smpte2084 ] && [ "$acount" -ge 1 ]; then
    mv -f "$TMP" "$OUT"
    if [ "${DELETE_SOURCE:-0}" = 1 ]; then rm -f "$SRC"; note="original deleted"; else note="original kept"; fi
    echo "[done]  $(date +%T) $OUT (src ${src_dur}s, out ${out_dur}s) - $note"
  else
    echo "[FAIL]  $(date +%T) $SRC rc=${rc[*]} src=${src_dur} out=${out_dur} trc=${trc} audio=${acount} - original kept"
  fi
done

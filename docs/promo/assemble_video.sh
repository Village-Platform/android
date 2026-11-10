#!/usr/bin/env bash
# Builds the tutorial video (issue #28) from the screen clips (record_clips.sh) and the Gemini TTS
# narration (narration_ar_eg.md): each segment lasts as long as its narration plus a short pause,
# the phone clip sits on the brand background, and the last frame holds if the clip is shorter.
#
#   CLIPS=/tmp/clips AUDIO=/tmp/narration ./docs/promo/assemble_video.sh
#
# Output: ../village-platform/public/promo/video/aghour_tutorial_ar_eg_v10.1.mp4 (1080x1920, H.264/AAC).
# Needs ffmpeg and ffprobe (sudo apt install ffmpeg).
set -euo pipefail
cd "$(dirname "$0")"
CLIPS=${CLIPS:-/tmp/clips}
AUDIO=${AUDIO:-/tmp/narration}
PUBLIC=$(cd ../../../village-platform/public && pwd)
OUT=${OUT:-$PUBLIC/promo/video/aghour_tutorial_ar_eg_v10.1.mp4}
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
PAUSE=0.6
BG="$PUBLIC/brand/web/scene.jpg"

# segment number -> visual: a clip name, or card:<image>
declare -A VISUAL=(
  [01]="card:$PUBLIC/promo/screenshots/feature_graphic_1024x500.png"
  [02]=01_home [03]=02_category [04]=03_firm [05]=04_explore [06]=05_news_photos [07]=06_account
  [08]="card:$PUBLIC/promo/social/post_download_1080.png"
)

duration() { ffprobe -v error -show_entries format=duration -of csv=p=0 "$1"; }

: > "$WORK/list.txt"
for n in 01 02 03 04 05 06 07 08; do
  audio="$AUDIO/seg$n.wav"
  [ -f "$audio" ] || { echo "missing $audio"; exit 1; }
  len=$(python3 -c "print(round($(duration "$audio") + $PAUSE, 2))")
  visual=${VISUAL[$n]}
  seg="$WORK/seg$n.mp4"
  # Darkened, blurred village photo as the frame behind the content.
  bg="[0:v]scale=1080:1920:force_original_aspect_ratio=increase,crop=1080:1920,boxblur=20,eq=brightness=-0.25[bg]"
  if [[ $visual == card:* ]]; then
    ffmpeg -v error -y -loop 1 -i "$BG" -loop 1 -i "${visual#card:}" -i "$audio" -filter_complex \
      "$bg;[1:v]scale=1000:-1[fg];[bg][fg]overlay=(W-w)/2:(H-h)/2,format=yuv420p[v];[2:a]apad[a]" \
      -map "[v]" -map "[a]" -t "$len" -r 30 -c:v libx264 -preset medium -crf 20 -c:a aac -ar 48000 -ac 2 "$seg"
  else
    # tpad holds the clip's last frame for as long as the narration still runs.
    ffmpeg -v error -y -loop 1 -i "$BG" -i "$CLIPS/$visual.mp4" -i "$audio" -filter_complex \
      "$bg;[1:v]fps=30,scale=-1:1700,tpad=stop_mode=clone:stop_duration=30[fg];[bg][fg]overlay=(W-w)/2:(H-h)/2,format=yuv420p[v];[2:a]apad[a]" \
      -map "[v]" -map "[a]" -t "$len" -r 30 -c:v libx264 -preset medium -crf 20 -c:a aac -ar 48000 -ac 2 "$seg"
  fi
  echo "file '$seg'" >> "$WORK/list.txt"
  echo "segment $n: $len s"
done

mkdir -p "$(dirname "$OUT")"
ffmpeg -v error -y -f concat -safe 0 -i "$WORK/list.txt" -c copy -movflags +faststart "$OUT"
echo "wrote $OUT ($(duration "$OUT") s)"

#!/usr/bin/env bash
# Records one screen clip per narration segment from a running emulator/device with the app
# installed and signed in (issue #28). Clips land in $OUT (default /tmp/clips) as NN_name.mp4.
# Coordinates are for a 1080x2400 screen (the Medium_Phone AVD).
#
# Uses the emulator's own recorder (`adb emu screenrecord`): on a headless emulator the on-device
# `screenrecord` encodes only the first frame. SERIAL pins the emulator when a phone is plugged in too.
set -euo pipefail
SERIAL=${SERIAL:-emulator-5554}
ADB="${ADB_BIN:-$HOME/Android/Sdk/platform-tools/adb} -s $SERIAL"
OUT=${OUT:-/tmp/clips}
PKG=${PKG:-com.aghourservices.dev}
mkdir -p "$OUT"

tap()   { $ADB shell input tap "$1" "$2"; }
swipe() { $ADB shell input swipe "$@"; }
tab()   { case $1 in home) tap 950 2270;; explore) tap 678 2270;; news) tap 402 2270;; account) tap 127 2270;; esac; }

# record NAME SECONDS -- starts the recorder, runs the actions read from stdin, waits for the time
# limit, then converts the WebM to an H.264 MP4 at 720x1600.
record() {
  local name=$1 secs=$2
  local webm="$OUT/$name.webm"
  rm -f "$webm"
  $ADB emu screenrecord start --time-limit "$secs" "$webm" >/dev/null
  local started=$SECONDS
  ADB="$ADB" bash -c "$(declare -f tap swipe tab); $(cat)"
  sleep $(( secs - (SECONDS - started) + 2 > 0 ? secs - (SECONDS - started) + 2 : 2 ))
  ffmpeg -v error -y -i "$webm" -vf scale=720:1600,fps=30 -c:v libx264 -crf 20 -pix_fmt yuv420p "$OUT/$name.mp4"
  rm -f "$webm"
  echo "recorded $name ($(ffprobe -v error -show_entries format=duration -of csv=p=0 "$OUT/$name.mp4")s)"
}

# Clean status bar: 10:00, full battery and signal, no notifications.
$ADB shell settings put global sysui_demo_allowed 1
demo() { $ADB shell am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null; }
demo enter; demo clock -e hhmm 1000; demo battery -e level 100 -e plugged false
demo network -e wifi show -e level 4 -e mobile hide; demo notifications -e visible false

$ADB shell am force-stop "$PKG"
$ADB shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 5

record 01_home 9 <<'A'
sleep 3; swipe 540 1700 540 900 900; sleep 2; swipe 540 900 540 1700 700
A
record 02_category 8 <<'A'
tap 956 504; sleep 4; swipe 540 1700 540 1000 900
A
record 03_firm 10 <<'A'
tap 540 700; sleep 4; swipe 540 1700 540 1100 900; sleep 2
A
record 04_explore 10 <<'A'
$ADB shell input keyevent 4; sleep 1; $ADB shell input keyevent 4; sleep 1; tab explore; sleep 3; swipe 540 1700 540 1000 900
A
record 05_news_photos 12 <<'A'
tab news; sleep 3; tap 540 550; sleep 4; tap 770 1150; sleep 2; swipe 200 1200 900 1200 400; sleep 1.5; swipe 200 1200 900 1200 400
A
record 06_account 9 <<'A'
$ADB shell input keyevent 4; sleep 1; $ADB shell input keyevent 4; sleep 1; tab account; sleep 3; tap 520 1126; sleep 3
A
$ADB shell input keyevent 4; sleep 1; tab home

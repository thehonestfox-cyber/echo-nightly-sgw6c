#!/usr/bin/env bash
# Smoke test executed inside the Wear OS emulator job of the CI workflow.
#
# For every APK: install it, launch the main activity, tap through the main screens with
# uiautomator and make sure the process is still alive and no FATAL EXCEPTION was logged.
# Everything interesting ends up in ./smoke (markdown report, logcat, screenshots).
set -u

ACTIVITY="dev.brahmkshatriya.echo.ui.MainActivity"
OUT="smoke"
REPORT="$OUT/report.md"
STATUS=0
mkdir -p "$OUT"
: > "$REPORT"

adb wait-for-device
adb shell wm dismiss-keyguard >/dev/null 2>&1 || true
adb shell settings put global window_animation_scale 0 >/dev/null 2>&1 || true
adb shell settings put global transition_animation_scale 0 >/dev/null 2>&1 || true
adb shell settings put global animator_duration_scale 0 >/dev/null 2>&1 || true
{
  echo "Emulator: $(adb shell getprop ro.build.fingerprint | tr -d '\r')"
  echo "Display: $(adb shell wm size | tr -d '\r')"
} > "$OUT/device.txt"

# Dump the UI hierarchy and tap the centre of the first node whose text/description matches.
tap_text() {
  local pattern="$1"
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || return 1
  adb pull /sdcard/ui.xml "$OUT/ui.xml" >/dev/null 2>&1 || return 1
  local coords
  coords=$(python3 - "$OUT/ui.xml" "$pattern" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding="utf-8", errors="ignore").read()
pat = re.compile(sys.argv[2], re.I)
for node in re.finditer(r"<node [^>]*>", xml):
    n = node.group(0)
    text = re.search(r'text="([^"]*)"', n)
    desc = re.search(r'content-desc="([^"]*)"', n)
    hay = " ".join(x.group(1) for x in (text, desc) if x)
    if not pat.search(hay):
        continue
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
    if not b:
        continue
    x1, y1, x2, y2 = map(int, b.groups())
    print((x1 + x2) // 2, (y1 + y2) // 2)
    break
PY
)
  [ -n "$coords" ] || return 1
  # shellcheck disable=SC2086
  adb shell input tap $coords
}

screen_size() {
  adb shell wm size | tr -d '\r' | sed -n 's/.*: *\([0-9]*\)x\([0-9]*\).*/\1 \2/p' | tail -n 1
}

# Scroll the list until a node matching the pattern is visible, then tap it.
find_and_tap() {
  local pattern="$1" size w h i
  size=$(screen_size); w=${size% *}; h=${size#* }
  for i in 1 2 3 4 5; do
    tap_text "$pattern" && return 0
    adb shell input swipe $((w / 2)) $((h * 3 / 4)) $((w / 2)) $((h / 4)) 300; sleep 1
  done
  for i in 1 2 3 4 5 6 7 8 9 10; do
    tap_text "$pattern" && return 0
    adb shell input swipe $((w / 2)) $((h / 4)) $((w / 2)) $((h * 3 / 4)) 300; sleep 1
  done
  return 1
}

# Swipe from the left edge to go back (Wear OS swipe-to-dismiss).
swipe_back() {
  local size w h
  size=$(screen_size); w=${size% *}; h=${size#* }
  adb shell input swipe 5 $((h / 2)) $((w - 10)) $((h / 2)) 300
}

alive() { [ -n "$(adb shell pidof "$1" | tr -d '\r')" ]; }

screenshot() { adb exec-out screencap -p > "$OUT/$1.png" 2>/dev/null || true; }

check_step() {
  # $1 = package, $2 = label, $3 = step name
  sleep 4
  screenshot "$2-$3"
  if ! alive "$1"; then
    echo "- ❌ **$3**: process died" >> "$REPORT"
    return 1
  fi
  if adb logcat -d | grep -q "FATAL EXCEPTION"; then
    echo "- ❌ **$3**: FATAL EXCEPTION logged" >> "$REPORT"
    return 1
  fi
  echo "- ✅ $3" >> "$REPORT"
}

test_apk() {
  local apk="$1" pkg="$2" label="$3" failed=0
  echo "### $label — \`$pkg\`" >> "$REPORT"
  echo >> "$REPORT"

  adb uninstall "$pkg" >/dev/null 2>&1 || true
  if ! adb install -r -g "$apk" > "$OUT/install-$label.txt" 2>&1; then
    echo "- ❌ **install failed**" >> "$REPORT"
    { echo '```'; cat "$OUT/install-$label.txt"; echo '```'; } >> "$REPORT"
    STATUS=1
    return
  fi
  echo "- ✅ install" >> "$REPORT"

  adb logcat -c
  adb shell am start -W -n "$pkg/$ACTIVITY" > "$OUT/start-$label.txt" 2>&1
  sleep 12
  check_step "$pkg" "$label" "launch (16 s)" || failed=1

  if [ "$failed" = 0 ]; then
    # Home -> Extensions -> back
    find_and_tap "No extension selected|Extensions" && check_step "$pkg" "$label" "extensions screen" || failed=1
    swipe_back; sleep 2
    # Home -> Settings -> About -> back x2
    if [ "$failed" = 0 ]; then
      find_and_tap "^Settings$" && check_step "$pkg" "$label" "settings screen" || failed=1
      swipe_back; sleep 2
    fi
    # Home -> Now playing (starts the player service) -> back
    if [ "$failed" = 0 ]; then
      find_and_tap "^Now playing$" && check_step "$pkg" "$label" "player screen (service start)" || failed=1
      swipe_back; sleep 2
    fi
    # Home -> Home feed (offline extension, empty library) -> back
    if [ "$failed" = 0 ]; then
      find_and_tap "^Home$" && check_step "$pkg" "$label" "home feed" || failed=1
      swipe_back; sleep 2
    fi
    # Background the app and bring it back
    if [ "$failed" = 0 ]; then
      adb shell input keyevent KEYCODE_HOME; sleep 2
      adb shell am start -n "$pkg/$ACTIVITY" >/dev/null 2>&1
      check_step "$pkg" "$label" "relaunch" || failed=1
    fi
  fi

  adb logcat -d > "$OUT/logcat-$label.txt"
  adb shell "run-as $pkg cat files/crash/last_crash.txt" > "$OUT/crash-$label.txt" 2>/dev/null || true

  if [ "$failed" != 0 ]; then
    STATUS=1
    echo >> "$REPORT"
    echo '```' >> "$REPORT"
    if grep -q "FATAL EXCEPTION" "$OUT/logcat-$label.txt"; then
      grep -n -A 70 "FATAL EXCEPTION" "$OUT/logcat-$label.txt" | head -160 | cut -c1-320 >> "$REPORT"
    elif [ -s "$OUT/crash-$label.txt" ]; then
      head -c 12000 "$OUT/crash-$label.txt" >> "$REPORT"
    else
      grep -n -E " E/| E | F/" "$OUT/logcat-$label.txt" | grep -v -E "audioserver|cameraserver|netd|Zygote|vold|wificond|BluetoothAdapter|GmsCore|com\.google\.android" | tail -80 | cut -c1-320 >> "$REPORT"
    fi
    echo '```' >> "$REPORT"
  fi

  # Non fatal errors of our own process are useful even when everything passed.
  local pid_errors
  pid_errors=$(grep -E "^[0-9-]+ [0-9:.]+ +[0-9]+ +[0-9]+ [EW] (Echo|Koin|AndroidRuntime|ExtensionLoader|PlayerService|MediaSession|ExoPlayer|System\.err)" "$OUT/logcat-$label.txt" | tail -40 | cut -c1-320)
  if [ -n "$pid_errors" ]; then
    {
      echo
      echo '<details><summary>Warnings / errors</summary>'
      echo
      echo '```'
      echo "$pid_errors"
      echo '```'
      echo '</details>'
    } >> "$REPORT"
  fi
  echo >> "$REPORT"
  adb shell am force-stop "$pkg" >/dev/null 2>&1 || true
}

test_apk out/echo-wear-nightly.apk dev.brahmkshatriya.echo.wear.nightly nightly
test_apk out/echo-wear-debug.apk dev.brahmkshatriya.echo.wear.debug debug

{
  echo
  echo '```'
  cat "$OUT/device.txt"
  echo '```'
} >> "$REPORT"

cat "$REPORT"
exit $STATUS

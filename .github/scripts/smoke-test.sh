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

# Dump the UI hierarchy to $OUT/ui.xml (uiautomator waits for the UI to become idle).
dump_ui() {
  local msg
  adb shell rm -f /sdcard/ui.xml >/dev/null 2>&1
  msg=$(adb shell uiautomator dump /sdcard/ui.xml 2>&1 | tr -d '\r')
  echo "$(date +%T) $msg" >> "$OUT/uiautomator.log"
  adb pull /sdcard/ui.xml "$OUT/ui.xml" >/dev/null 2>&1
}

# Print "x y" of the centre of the first node whose text/content-desc matches the pattern.
node_coords() {
  python3 - "$OUT/ui.xml" "$1" <<'PY' 2>/dev/null
import re, sys
try:
    xml = open(sys.argv[1], encoding="utf-8", errors="ignore").read()
except OSError:
    sys.exit(0)
pat = re.compile(sys.argv[2], re.I)
for node in re.finditer(r"<node [^>]*>", xml):
    n = node.group(0)
    values = [v for v in re.findall(r'(?:text|content-desc)="([^"]*)"', n) if v.strip()]
    if not any(pat.search(v.strip()) for v in values):
        continue
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
    if not b:
        continue
    x1, y1, x2, y2 = map(int, b.groups())
    print((x1 + x2) // 2, (y1 + y2) // 2)
    break
PY
}

# Tap the centre of the first node whose text/description matches.
tap_text() {
  local coords
  dump_ui || return 1
  coords=$(node_coords "$1")
  [ -n "$coords" ] || return 1
  # shellcheck disable=SC2086
  adb shell input tap $coords
}

# Succeeds if a node matching the pattern is currently on screen.
has_text() {
  dump_ui || return 1
  [ -n "$(node_coords "$1")" ]
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

scroll_top() {
  local size w h i
  size=$(screen_size); w=${size% *}; h=${size#* }
  for i in 1 2 3 4; do adb shell input swipe $((w / 2)) $((h / 4)) $((w / 2)) $((h * 3 / 4)) 200; done
  sleep 1
}

# The "Now playing" chip is the first item of the home screen and appears nowhere else.
on_home() { scroll_top; has_text "^Now playing$"; }

# Go back to the home screen: swipe-to-dismiss first, system back as a fallback.
back_home() {
  # $1 = label, $2 = step name (for the report)
  swipe_back; sleep 2
  on_home && return 0
  adb shell input keyevent KEYCODE_BACK; sleep 2
  if on_home; then
    echo "- ⚠️ swipe-to-dismiss did not leave the *$2* (system back worked)" >> "$REPORT"
    return 0
  fi
  screenshot "$1-$2-back"
  echo "- ⚠️ could not get back to the home screen after *$2* (visible texts: $(visible_texts))" >> "$REPORT"
  return 1
}

alive() { [ -n "$(adb shell pidof "$1" | tr -d '\r')" ]; }

screenshot() { adb exec-out screencap -p > "$OUT/$1.png" 2>/dev/null || true; }

# Texts currently visible on screen (from the last uiautomator dump), for diagnostics.
visible_texts() {
  python3 - "$OUT/ui.xml" <<'PY' 2>/dev/null
import re, sys
try:
    xml = open(sys.argv[1], encoding="utf-8", errors="ignore").read()
except OSError:
    sys.exit(0)
seen = []
for m in re.finditer(r'(?:text|content-desc)="([^"]+)"', xml):
    if m.group(1) not in seen:
        seen.append(m.group(1))
print(" | ".join(seen[:40]))
PY
}

# Scroll to a node matching $4 and tap it, then run check_step for step $3.
navigate() {
  # $1 = package, $2 = label, $3 = step name, $4 = text pattern
  if ! alive "$1"; then
    echo "- ❌ **$3**: process already dead before navigating" >> "$REPORT"
    return 1
  fi
  if ! find_and_tap "$4"; then
    screenshot "$2-$3-notfound"
    if alive "$1"; then
      echo "- ❌ **$3**: no UI element matching \`$4\` (visible texts: $(visible_texts); uiautomator: $(tail -n 1 "$OUT/uiautomator.log" 2>/dev/null))" >> "$REPORT"
    else
      echo "- ❌ **$3**: process died while looking for \`$4\`" >> "$REPORT"
    fi
    return 1
  fi
  check_step "$1" "$2" "$3"
}

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
  local app_pid
  app_pid=$(adb shell pidof "$pkg" | tr -d '\r' | awk '{print $1}')
  check_step "$pkg" "$label" "launch (16 s)" || failed=1

  if [ "$failed" = 0 ]; then
    # Home -> Extensions -> back
    navigate "$pkg" "$label" "extensions screen" "No extension selected|Extensions" || failed=1
    [ "$failed" = 0 ] && { back_home "$label" "extensions screen" || failed=1; }
    # Home -> Settings -> back
    if [ "$failed" = 0 ]; then
      navigate "$pkg" "$label" "settings screen" "^Settings$" || failed=1
      [ "$failed" = 0 ] && { back_home "$label" "settings screen" || failed=1; }
    fi
    # Home -> Now playing (starts the player service) -> back
    if [ "$failed" = 0 ]; then
      navigate "$pkg" "$label" "player screen (service start)" "^Now playing$" || failed=1
      [ "$failed" = 0 ] && { back_home "$label" "player screen" || failed=1; }
    fi
    # Home -> Home feed (offline extension, empty library) -> back
    if [ "$failed" = 0 ]; then
      navigate "$pkg" "$label" "home feed" "^Home$" || failed=1
      [ "$failed" = 0 ] && { back_home "$label" "home feed" || failed=1; }
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
      # Errors of our own process (by pid), native crash dumps and process-death notices.
      grep -n -E "^[0-9-]+ [0-9:.]+ +(${app_pid:-0}) +[0-9]+ [EF] | F DEBUG |ActivityManager: (Process $pkg|Killing|Force stopping $pkg)|has died" "$OUT/logcat-$label.txt" | tail -80 | cut -c1-320 >> "$REPORT"
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

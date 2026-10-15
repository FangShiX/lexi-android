#!/usr/bin/env bash
set -euo pipefail
mkdir -p device-reports
trap 'adb logcat -d > device-reports/logcat.txt 2>&1 || true' EXIT
adb install -r candidate/outputs/apk/debug/app-debug.apk
adb install -r candidate/outputs/apk/androidTest/debug/app-debug-androidTest.apk
run_tests() {
  local scenario="$1"
  shift
  mkdir -p "device-reports/$scenario"
  adb shell am instrument -w "$@" io.github.fangshix.lexi.test/androidx.test.runner.AndroidJUnitRunner | tee "device-reports/$scenario/result.txt"
  python3 - "$scenario" <<'PY'
import re,sys
from pathlib import Path
text=(Path('device-reports')/sys.argv[1]/'result.txt').read_text()
assert re.search(r'OK \([1-9][0-9]* tests?\)',text), 'Instrumentation did not report passing tests'
assert 'FAILURES!!!' not in text and 'Process crashed' not in text
PY
  adb pull /sdcard/Android/data/io.github.fangshix.lexi/files/frontend-preview "device-reports/$scenario/screenshots" || true
}
run_tests default
adb shell wm size 640x360
adb shell wm density 160
adb shell settings put system font_scale 1.3
run_tests landscape-large-font -e class com.finisdiscago.leximvp.feature.profile.FrontendJourneyTest
adb shell wm size 320x640
adb shell settings put system font_scale 1.0
run_tests compact -e class com.finisdiscago.leximvp.feature.profile.FrontendJourneyTest

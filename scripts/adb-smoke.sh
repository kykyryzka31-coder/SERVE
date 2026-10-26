#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${ANDROID_SERIAL:?Set ANDROID_SERIAL from adb devices -l}"
ADB="${ADB:-adb}"
"$ADB" -s "$ANDROID_SERIAL" get-state
if [[ "$("$ADB" -s "$ANDROID_SERIAL" shell getprop sys.boot_completed | tr -d '\r')" != 1 ]]; then
  echo 'Android has not completed boot' >&2; exit 1
fi
apk=app/build/outputs/apk/debug/app-debug.apk
[[ -f "$apk" ]] || { echo 'Build APK first: ./gradlew assembleDebug' >&2; exit 1; }
"$ADB" -s "$ANDROID_SERIAL" install -r "$apk"
result=$("$ADB" -s "$ANDROID_SERIAL" shell am start -W -n dev.serve.riseautomation/.MainActivity)
printf '%s\n' "$result"
[[ "$result" == *'Status: ok'* ]] || exit 1
"$ADB" -s "$ANDROID_SERIAL" shell pidof dev.serve.riseautomation
"$ADB" -s "$ANDROID_SERIAL" shell uiautomator dump /sdcard/serve-window.xml >/dev/null
"$ADB" -s "$ANDROID_SERIAL" shell cat /sdcard/serve-window.xml | python3 -c 'import sys,xml.etree.ElementTree as E; r=E.fromstring(sys.stdin.read()); assert any(n.get("text")=="Rise Automation" for n in r.iter("node")), "App title missing"; assert any(n.get("text", "").startswith("Успешных действий:") for n in r.iter("node")), "Session statistics missing"'

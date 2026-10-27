#!/usr/bin/env bash
set -euo pipefail
if ! command -v gradle >/dev/null 2>&1; then
  echo "Gradle bulunamadi. Android Studio ile projeyi acip Build APK secenegini kullanin." >&2
  exit 1
fi
gradle assembleDebug
echo "APK: app/build/outputs/apk/debug/app-debug.apk"

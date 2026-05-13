#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

echo "==> Building RetakID release APK..."
./gradlew assembleRelease

APK="app/build/outputs/apk/release/app-release.apk"
DEST="retakid-v1.1.0.apk"

if [ -f "$APK" ]; then
  cp "$APK" "$DEST"
  echo "==> APK built: $DEST"
else
  echo "==> Build failed or APK not found at $APK"
  exit 1
fi

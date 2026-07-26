#!/usr/bin/env bash
# Release validation gate — real Android build only (no placeholder APK path).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

export COREGUARD_ANDROID_BUILD="${COREGUARD_ANDROID_BUILD:-true}"

echo "[validate-release] clean"
./gradlew clean --no-daemon

echo "[validate-release] unit tests + lintRelease + assembleRelease + bundleRelease"
./gradlew \
  :app:testDebugUnitTest \
  :app:lintRelease \
  :app:assembleRelease \
  :app:bundleRelease \
  --no-daemon --stacktrace

AAB=$(find app/build/outputs/bundle/release -name '*.aab' | head -n 1 || true)
APK=$(find app/build/outputs/apk/release -name '*.apk' | head -n 1 || true)
if [[ -z "${AAB}" ]]; then
  echo "[validate-release] FAIL: missing release AAB" >&2
  exit 1
fi
if [[ -z "${APK}" ]]; then
  echo "[validate-release] FAIL: missing release APK" >&2
  exit 1
fi

echo "[validate-release] AAB=${AAB}"
sha256sum "${AAB}" | tee app/build/outputs/bundle/release/checksums-sha256.txt
echo "[validate-release] PASS"

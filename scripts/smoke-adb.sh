#!/usr/bin/env bash
# Device/emulator smoke: install debug APK, launch, assert process + no fatal.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/android-sdk}}"
export ANDROID_HOME="$SDK_ROOT" ANDROID_SDK_ROOT="$SDK_ROOT"
export PATH="$SDK_ROOT/platform-tools:$PATH"

PACKAGE="${PACKAGE:-com.coldboar.coreguard.debug}"
APK="${APK:-$ROOT/app/build/outputs/apk/debug/app-debug.apk}"
OUT_DIR="${OUT_DIR:-/tmp/coreguard-smoke}"
mkdir -p "$OUT_DIR"

if ! adb get-state >/dev/null 2>&1; then
  echo "[smoke] No adb device. Run ./scripts/run-emulator.sh first (or connect a phone)." >&2
  exit 1
fi

if [[ ! -f "$APK" ]]; then
  echo "[smoke] Building debug APK…"
  (cd "$ROOT" && ./gradlew -Pcoreguard.androidBuild=true :app:assembleDebug)
fi

echo "[smoke] Waiting for PackageManager…"
for i in $(seq 1 90); do
  if adb shell pm path android >/dev/null 2>&1; then break; fi
  sleep 2
done

echo "[smoke] Installing $APK"
# -d allows downgrade on shared AVDs that retained a newer versionCode.
if ! adb install -r -d -t -g "$APK"; then
  echo "[smoke] install failed — uninstalling $PACKAGE and retrying…"
  adb uninstall "$PACKAGE" >/dev/null 2>&1 || true
  adb install -r -d -t -g "$APK"
fi

echo "[smoke] Clearing logcat + launching"
adb logcat -c || true
adb shell am force-stop "$PACKAGE" || true
# Avoid am start -W on soft/no-KVM hosts: it often reports LaunchState UNKNOWN /
# Status: timeout while the activity is still coming up. Start async, then poll pid.
adb shell am start -n "$PACKAGE/com.coldboar.coreguard.MainActivity" | tee "$OUT_DIR/am-start.txt"

echo "[smoke] Waiting for process…"
PID=""
for _ in $(seq 1 60); do
  PID="$(adb shell pidof -s "$PACKAGE" 2>/dev/null | tr -d '\r' || true)"
  if [[ -n "$PID" ]]; then
    break
  fi
  sleep 1
done

echo "[smoke] Settling…"
sleep 8

PID="$(adb shell pidof -s "$PACKAGE" 2>/dev/null | tr -d '\r' || true)"
FOCUS="$(adb shell dumpsys window 2>/dev/null | grep mCurrentFocus | head -1 || true)"
echo "[smoke] pid=$PID"
echo "[smoke] $FOCUS"

adb shell screencap -p /sdcard/coreguard_smoke.png || true
adb pull /sdcard/coreguard_smoke.png "$OUT_DIR/coreguard_smoke.png" >/dev/null 2>&1 || true
adb logcat -d -t 400 > "$OUT_DIR/logcat.txt"

FATAL=0
if grep -E "FATAL EXCEPTION:.*$PACKAGE|Process: $PACKAGE" "$OUT_DIR/logcat.txt" >/dev/null; then
  FATAL=1
fi

if [[ -z "$PID" ]]; then
  echo "[smoke] FAIL: process not running" >&2
  exit 2
fi
if [[ "$FATAL" == "1" ]]; then
  echo "[smoke] FAIL: fatal exception in logcat (see $OUT_DIR/logcat.txt)" >&2
  exit 3
fi

echo "[smoke] PASS — process alive, no fatal for $PACKAGE"
echo "[smoke] Artifacts: $OUT_DIR"

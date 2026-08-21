#!/usr/bin/env bash
# Boot CoreGuard_API36, install the debug APK, and launch the app.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/android-sdk}}"
export ANDROID_HOME="$SDK_ROOT"
export ANDROID_SDK_ROOT="$SDK_ROOT"
export PATH="$SDK_ROOT/cmdline-tools/latest/bin:$SDK_ROOT/platform-tools:$SDK_ROOT/emulator:$PATH"

# Prefer lean ATD AVD when present (faster instrumented tests without KVM).
if [[ -z "${AVD_NAME:-}" ]]; then
  if avdmanager list avd 2>/dev/null | grep -q 'CoreGuard_ATD36'; then
    AVD_NAME="CoreGuard_ATD36"
  else
    AVD_NAME="CoreGuard_API36"
  fi
fi
PACKAGE_DEBUG="com.coldboar.coreguard.debug"
APK="${APK:-$ROOT/app/build/outputs/apk/debug/app-debug.apk}"

if [[ ! -x "$SDK_ROOT/emulator/emulator" ]]; then
  echo "[run] Android SDK/emulator missing. Run ./scripts/setup-android-sdk.sh first." >&2
  exit 1
fi

if ! avdmanager list avd 2>/dev/null | grep -q "$AVD_NAME"; then
  echo "[run] AVD $AVD_NAME not found. Run ./scripts/setup-android-sdk.sh first." >&2
  exit 1
fi

if [[ ! -f "$APK" ]]; then
  echo "[run] Building debug APK…"
  (cd "$ROOT" && ./gradlew -Pcoreguard.androidBuild=true :app:assembleDebug)
fi

# Prefer hardware accel only when /dev/kvm is actually usable. A present but
# non-writable /dev/kvm (common on CI runners without the kvm udev rule) would
# otherwise select hardware accel that cannot start.
EMU_ARGS=(-avd "$AVD_NAME" -no-audio -no-boot-anim -netdelay none -netspeed full)
if [[ -w /dev/kvm ]]; then
  EMU_ARGS+=(-gpu auto)
else
  if [[ -e /dev/kvm ]]; then
    echo "[run] WARNING: /dev/kvm exists but is not writable — using software emulation (slow)."
  else
    echo "[run] WARNING: /dev/kvm not available — using software emulation (slow)."
  fi
  EMU_ARGS+=(-gpu swiftshader_indirect -accel off)
fi

if ! adb devices 2>/dev/null | grep -qE 'emulator-[0-9]+\s+device'; then
  echo "[run] Starting emulator $AVD_NAME…"
  # -no-window when HEADLESS=1 (CI / remote); default shows window on desktop hosts.
  if [[ "${HEADLESS:-0}" == "1" ]]; then
    EMU_ARGS+=(-no-window)
  fi
  "$SDK_ROOT/emulator/emulator" "${EMU_ARGS[@]}" >/tmp/coreguard-emulator.log 2>&1 &
  echo $! > /tmp/coreguard-emulator.pid
  echo "[run] Waiting for device…"
  # Bounded wait: an unbounded `adb wait-for-device` hangs until the CI job
  # timeout, hiding the real cause (e.g. no KVM). Fail closed with diagnostics.
  DEVICE_TIMEOUT="${EMULATOR_DEVICE_TIMEOUT:-600}"
  if ! timeout "$DEVICE_TIMEOUT" adb wait-for-device; then
    echo "[run] ERROR: emulator did not come online within ${DEVICE_TIMEOUT}s." >&2
    echo "[run] /dev/kvm present: $([[ -e /dev/kvm ]] && echo yes || echo no)" >&2
    echo "[run] --- last 80 lines of emulator log ---" >&2
    tail -n 80 /tmp/coreguard-emulator.log >&2 || true
    exit 1
  fi
  # Wait until boot + PackageManager are actually ready (boot_completed alone is not enough).
  boot_ready=0
  for i in $(seq 1 180); do
    boot="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
    if [[ "$boot" == "1" ]] && adb shell pm path android >/dev/null 2>&1; then
      echo "[run] Boot + PackageManager ready (${i} checks)."
      boot_ready=1
      break
    fi
    sleep 2
  done
  if [[ "$boot_ready" -ne 1 ]]; then
    echo "[run] ERROR: emulator booted the device node but sys.boot_completed/PackageManager never became ready." >&2
    echo "[run] --- last 80 lines of emulator log ---" >&2
    tail -n 80 /tmp/coreguard-emulator.log >&2 || true
    exit 1
  fi
  # Settle storage / PM after first boot on cold images.
  sleep 8
fi

echo "[run] Installing $APK…"
# Prefer component launch; package-only MAIN/LAUNCHER can fail on some images.
install_debug_apk() {
  local attempt out
  for attempt in 1 2 3 4 5; do
    # -d allows downgrade so gates survive versionCode bumps on shared AVDs.
    if out="$(adb install -r -d -t "$APK" 2>&1)"; then
      echo "$out"
      return 0
    fi
    echo "$out"
    if echo "$out" | grep -q 'INSTALL_FAILED_VERSION_DOWNGRADE'; then
      echo "[run] VERSION_DOWNGRADE — uninstalling $PACKAGE_DEBUG and retrying…"
      adb uninstall "$PACKAGE_DEBUG" >/dev/null 2>&1 || true
    fi
    echo "[run] install attempt $attempt failed — waiting for PackageManager…"
    sleep 8
  done
  return 1
}
if ! install_debug_apk; then
  echo "[run] Failed to install APK after retries." >&2
  exit 1
fi
echo "[run] Launching $PACKAGE_DEBUG…"
if ! adb shell am start -n "$PACKAGE_DEBUG/com.coldboar.coreguard.MainActivity"; then
  adb shell am start -a android.intent.action.MAIN \
    -c android.intent.category.LAUNCHER \
    -p "$PACKAGE_DEBUG" || true
fi

echo "[run] App launched (debug package). Prefer a KVM host for snappy UI testing."
echo "[run] Logs: adb logcat --pid=\$(adb shell pidof -s $PACKAGE_DEBUG)"

#!/usr/bin/env bash
# Quilla emulator gate: boot AVD (prefer ATD), unit tests, am-instrument, smoke.
# Usage: HEADLESS=1 ./scripts/quilla-emulator-tests.sh
#
# On no-KVM hosts, Gradle :connectedDebugAndroidTest often ANRs during cold
# process start. We install via Gradle, then drive AndroidJUnitRunner with
# `adb shell am instrument` (stable on CoreGuard_ATD35).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/android-sdk}}"
export ANDROID_HOME="$SDK_ROOT" ANDROID_SDK_ROOT="$SDK_ROOT"
export PATH="$SDK_ROOT/cmdline-tools/latest/bin:$SDK_ROOT/platform-tools:$SDK_ROOT/emulator:$PATH"
export HEADLESS="${HEADLESS:-1}"

OUT_DIR="${OUT_DIR:-/tmp/quilla-emulator-gate}"
mkdir -p "$OUT_DIR"
RESULTS="$OUT_DIR/results.txt"
: > "$RESULTS"

RUNNER="com.coldboar.coreguard.debug.test/androidx.test.runner.AndroidJUnitRunner"
TEST_CLASSES=(
  "com.coldboar.coreguard.quilla.QuillaQuantumOnDeviceTest"
  "com.coldboar.coreguard.quilla.MainActivityLaunchTest"
)

log() { echo "[quilla-emu] $*" | tee -a "$RESULTS"; }

run_instrument_class() {
  local cls="$1"
  local attempt out
  for attempt in 1 2 3; do
    out="$OUT_DIR/instr-${cls##*.}-try${attempt}.txt"
    log "instrument $cls (attempt $attempt)…"
    if timeout 240 adb shell am instrument -w -r -e class "$cls" "$RUNNER" | tee "$out"; then
      if grep -qE 'OK \([0-9]+ tests?\)' "$out"; then
        return 0
      fi
    fi
    log "WARN: $cls attempt $attempt incomplete — settling…"
    sleep 8
  done
  return 1
}

cd "$ROOT"

log "1/5 Boot emulator (prefer CoreGuard_ATD35)…"
./scripts/run-emulator.sh | tee -a "$OUT_DIR/emulator.log"
adb shell settings put global window_animation_scale 0 >/dev/null 2>&1 || true
adb shell settings put global transition_animation_scale 0 >/dev/null 2>&1 || true
adb shell settings put global animator_duration_scale 0 >/dev/null 2>&1 || true

GRADLE_FLAGS=(-Pcoreguard.androidBuild=true)
# Optional lean ABI when the property exists in android-app.gradle
if grep -q 'coreguard.emulatorAbi' "$ROOT/gradle/android-app.gradle" 2>/dev/null; then
  GRADLE_FLAGS+=(-Pcoreguard.emulatorAbi=x86_64)
fi

log "2/5 Unit tests…"
./gradlew "${GRADLE_FLAGS[@]}" :app:testDebugUnitTest | tee "$OUT_DIR/unit-tests.log"
UNIT_OK=1

log "3/5 Install debug + androidTest…"
./gradlew "${GRADLE_FLAGS[@]}" :app:installDebug :app:installDebugAndroidTest | tee "$OUT_DIR/install.log"
adb shell cmd package compile -m speed -f com.coldboar.coreguard.debug >/dev/null 2>&1 || true
sleep 5

log "4/5 Instrumented tests via am instrument…"
INSTR_OK=1
for cls in "${TEST_CLASSES[@]}"; do
  if ! run_instrument_class "$cls"; then
    INSTR_OK=0
    log "FAIL: $cls"
  fi
done

log "5/5 ADB smoke…"
./scripts/smoke-adb.sh | tee "$OUT_DIR/smoke.log"
SMOKE_OK=1

echo "UNIT_OK=$UNIT_OK INSTR_OK=$INSTR_OK SMOKE_OK=$SMOKE_OK" >> "$RESULTS"
if [[ "$UNIT_OK" -eq 1 && "$INSTR_OK" -eq 1 && "$SMOKE_OK" -eq 1 ]]; then
  log "PASS — unit + instrumented + smoke"
  log "Artifacts: $OUT_DIR"
  exit 0
fi
log "FAIL — see $OUT_DIR"
exit 1

#!/usr/bin/env bash
# Quilla deadline harness: boot CoreGuard_API35, unit tests, instrumented tests, smoke.
# Usage: HEADLESS=1 ./scripts/quilla-emulator-tests.sh
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

log() { echo "[quilla-emu] $*" | tee -a "$RESULTS"; }

cd "$ROOT"

log "1/4 Boot emulator (AVD CoreGuard_API35)…"
./scripts/run-emulator.sh | tee -a "$OUT_DIR/emulator.log"

log "2/4 Unit tests…"
./gradlew -Pcoreguard.androidBuild=true :app:testDebugUnitTest | tee "$OUT_DIR/unit-tests.log"
UNIT_OK=1

log "3/4 Connected instrumented tests…"
./gradlew -Pcoreguard.androidBuild=true :app:connectedDebugAndroidTest | tee "$OUT_DIR/android-tests.log"
INSTR_OK=1

log "4/4 ADB smoke…"
./scripts/smoke-adb.sh | tee "$OUT_DIR/smoke.log"
SMOKE_OK=1

log "PASS — unit + connectedDebugAndroidTest + smoke"
log "Artifacts: $OUT_DIR"
echo "UNIT_OK=$UNIT_OK INSTR_OK=$INSTR_OK SMOKE_OK=$SMOKE_OK" >> "$RESULTS"
exit 0

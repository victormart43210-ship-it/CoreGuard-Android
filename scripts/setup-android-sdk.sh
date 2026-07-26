#!/usr/bin/env bash
# One-time Android SDK bootstrap for CoreGuard local builds + emulator.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/android-sdk}}"
CMDTOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"

mkdir -p "$SDK_ROOT/cmdline-tools"
if [[ ! -x "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" ]]; then
  echo "[setup] Downloading Android command-line tools…"
  tmp="$(mktemp -d)"
  curl -L -o "$tmp/cmdtools.zip" "$CMDTOOLS_URL"
  unzip -q "$tmp/cmdtools.zip" -d "$tmp"
  rm -rf "$SDK_ROOT/cmdline-tools/latest"
  mv "$tmp/cmdline-tools" "$SDK_ROOT/cmdline-tools/latest"
  rm -rf "$tmp"
fi

export ANDROID_HOME="$SDK_ROOT"
export ANDROID_SDK_ROOT="$SDK_ROOT"
export PATH="$SDK_ROOT/cmdline-tools/latest/bin:$SDK_ROOT/platform-tools:$SDK_ROOT/emulator:$PATH"

echo "[setup] Accepting licenses…"
yes | sdkmanager --licenses >/dev/null || true

echo "[setup] Installing SDK packages…"
sdkmanager --install \
  "platforms;android-35" \
  "build-tools;35.0.0" \
  "platform-tools" \
  "ndk;26.1.10909125" \
  "cmake;3.22.1" \
  "emulator" \
  "system-images;android-35;google_apis;x86_64"

printf 'sdk.dir=%s\n' "$SDK_ROOT" > "$ROOT/local.properties"
echo "[setup] Wrote $ROOT/local.properties"

if ! avdmanager list avd 2>/dev/null | grep -q "CoreGuard_API35"; then
  echo "[setup] Creating AVD CoreGuard_API35…"
  echo no | avdmanager create avd \
    -n CoreGuard_API35 \
    -k "system-images;android-35;google_apis;x86_64" \
    -d pixel_6 \
    --force
fi

cat <<EOF

[setup] Done.

export ANDROID_HOME="$SDK_ROOT"
export ANDROID_SDK_ROOT="$SDK_ROOT"
export PATH="\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools:\$ANDROID_HOME/emulator:\$PATH"

Build debug APK:
  cd "$ROOT"
  ./gradlew -Pcoreguard.androidBuild=true :app:assembleDebug

Run emulator + install:
  ./scripts/run-emulator.sh
EOF

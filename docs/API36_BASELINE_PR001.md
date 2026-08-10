# Android 16 / API 36 Baseline — PR-001

Date: 2026-08-10  
Scope: Build/toolchain/CI baseline migration only (no architecture redesign).

## Final baseline

- `compileSdk`: 36
- `targetSdk`: 36
- `minSdk`: 24
- AGP: `8.7.3`
- Kotlin Gradle plugin: `1.9.25`
- Gradle wrapper: `8.13`
- Java/JVM target: 17
- NDK: `27.3.13750724`
- CMake: `3.31.5`

## CI baseline alignment

- Android CI SDK install uses:
  - `platforms;android-36`
  - `build-tools;36.0.0`
  - `ndk;27.3.13750724`
  - `cmake;3.31.5`
- Instrumentation gate runs on API-36 AVD (`CoreGuard_ATD36`) and now blocks on failures (no `continue-on-error`).

## Security/release guardrails preserved

- Release signing remains fail-closed via `COREGUARD_REQUIRE_RELEASE_SIGNING=true` gate in `gradle/android-app.gradle`.
- R8/minification/resource shrinking remain enabled in release build type.
- Debug placeholder/fake APK rejection guard (`verifyNoPlaceholderApk`) remains active.
- Real Android build remains default (`coreguard.androidBuild=true` unless explicitly disabled).

## Deferred Android 16 runtime work (PR-002)

Runtime compatibility evidence and any behavior-level follow-ups are tracked in:

- `docs/ANDROID_16_COMPATIBILITY.md`

This PR intentionally focuses on production build baseline and CI alignment.

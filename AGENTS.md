# AGENTS.md

## Cursor Cloud specific instructions

### Current repository state
This repository contains the **CoreGuard-Android** Kotlin/Compose app under `app/`,
plus docs, store assets, CLI, and CI scripts. Prefer `docs/RELEASE_READINESS.md`,
`docs/SECURITY_CLAIMS.md`, `docs/WEDNESDAY_PLAY_LAUNCH.md`, and
`docs/NINE_TEN_PASS_SUMMARY.md` for honest ship status.

### Toolchain in the VM
- **JDK 21** (`java -version` → OpenJDK 21) is pre-installed.
- **Gradle wrapper** exists (`./gradlew`).
- **Android SDK** may be missing; bootstrap with `./scripts/setup-android-sdk.sh`
  (writes `local.properties`, installs API 35 / build-tools / NDK / emulator /
  AVD `CoreGuard_API35`).

### When validating changes
```bash
./scripts/setup-android-sdk.sh   # once per machine when SDK is absent
./gradlew -Pcoreguard.androidBuild=true :app:assembleDebug
./gradlew -Pcoreguard.androidBuild=true :app:testDebugUnitTest
./gradlew -Pcoreguard.androidBuild=true :app:lintDebug
./scripts/run-emulator.sh        # needs /dev/kvm for usable speed
```

Play upload AAB: `./scripts/prepare-upload-keystore.sh` then
`./gradlew -Pcoreguard.androidBuild=true :app:bundleRelease`.

Without `-Pcoreguard.androidBuild=true`, the offline placeholder APK path still
runs for sandbox environments. Cloud VMs often lack KVM — prefer a laptop with
Android Studio / hardware accel for interactive UI testing.

If the Android SDK is missing, **do not fabricate** build/test/device results —
record the limitation and keep release-readiness wording honest.

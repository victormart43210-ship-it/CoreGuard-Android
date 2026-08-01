# AGENTS.md

## Cursor Cloud specific instructions

### Current repository state
This repository contains the **CoreGuard-Android** Kotlin/Compose app under `app/`,
plus docs, store assets, CLI, and CI scripts. Prefer `docs/RELEASE_READINESS.md`,
`docs/SECURITY_CLAIMS.md`, `docs/RELEASE_FREEZE.md`, and `docs/CERTIFICATES.md`
for honest ship status.

### Toolchain in the VM
- **JDK 21** (`java -version` → OpenJDK 21) is pre-installed.
- **Gradle wrapper** exists (`./gradlew`).
- **Android SDK** may be missing; bootstrap with `./scripts/setup-android-sdk.sh`
  (writes `local.properties`, installs API 35 / build-tools / NDK / emulator).

### When validating changes
```bash
./scripts/setup-android-sdk.sh   # once per machine when SDK is absent
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./scripts/run-emulator.sh        # needs /dev/kvm for usable speed
```

Real Android builds are the **default**. Do not pass `-Pcoreguard.androidBuild=false`
unless you intentionally want `:app:generatePlaceholderArtifact` (non-APK stub under
`app/build/placeholder-artifacts/` only).

Play upload AAB: `./scripts/prepare-upload-keystore.sh` then
`./gradlew :app:bundleRelease` (set `COREGUARD_REQUIRE_RELEASE_SIGNING=true` in CI).

If the Android SDK is missing, **do not fabricate** build/test/device results —
record the limitation and keep release-readiness wording honest.

### Release freeze
See `docs/RELEASE_FREEZE.md` — no new features until Internal Testing is stable.

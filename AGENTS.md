# AGENTS.md

## Cursor Cloud specific instructions

### Current repository state
This repository contains the **CoreGuard-Android** Kotlin/Compose app under `app/`,
plus docs, store assets, CLI, and CI scripts. Prefer `docs/RELEASE_READINESS.md`,
`docs/SECURITY_CLAIMS.md`, and `docs/NINE_TEN_PASS_SUMMARY.md` for honest ship status.

### Toolchain already available in the VM
- **JDK 21** (`java -version` → OpenJDK 21) is pre-installed.
- **Gradle wrapper** exists (`./gradlew`).
- **Not** always pre-installed: full Android SDK (`ANDROID_HOME` / `ANDROID_SDK_ROOT`
  may be unset). Real Android builds need:
  `./gradlew -Pcoreguard.androidBuild=true …` with SDK platforms/build-tools installed.

### When validating changes
- Unit tests: `./gradlew -Pcoreguard.androidBuild=true :app:testDebugUnitTest`
- Debug APK: `./gradlew -Pcoreguard.androidBuild=true :app:assembleDebug`
- Lint: `./gradlew -Pcoreguard.androidBuild=true :app:lintDebug`
- Manual device smoke: `docs/MANUAL_RELEASE_TEST.md`

If the Android SDK is missing, **do not fabricate** build/test/device results — record
the limitation and keep release-readiness wording honest.

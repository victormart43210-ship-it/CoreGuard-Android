# AGENTS.md

## Cursor Cloud specific instructions

### Current repository state
This repository is currently a **placeholder**. It contains only `README.md` and
`LICENSE`. There is **no application code, no Gradle/Android project, and no
dependency manifests** committed yet. As a result there is nothing to build,
lint, test, or run at this time, and no end-to-end flow can be exercised.

`README.md` describes the intended product: **CoreGuard-Android**, a native
Kotlin Android security and device-monitoring application.

### Toolchain already available in the VM
- **JDK 21** (`java -version` → OpenJDK 21) is pre-installed.
- **Not** pre-installed: Gradle, the Kotlin compiler (`kotlinc`), and the
  Android SDK (`ANDROID_HOME`/`ANDROID_SDK_ROOT` are unset; no `sdkmanager`/`adb`).

### When the Android project is scaffolded
Once an actual Android/Gradle project is added, the standard workflow will be
driven by the Gradle wrapper (`./gradlew`), e.g.:
- Build: `./gradlew assembleDebug`
- Unit tests: `./gradlew test`
- Lint: `./gradlew lint` (and/or `ktlint`/`detekt` if configured)

Building/running an Android app additionally requires installing the Android SDK
(command-line tools + platform + build-tools) and, for on-device runs, an
emulator (AVD) or connected device via `adb`. None of that is set up yet because
there is no project to validate it against — add it as part of scaffolding the app.
The startup update script is intentionally a guarded no-op until a Gradle wrapper
(`./gradlew`) exists.

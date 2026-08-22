# CoreGuard Android Phase-0 Current State

Generated UTC: 2026-08-22T00:55:28Z

## Repository

- Expected handoff SHA: `7be3268c40d1f498e411de8301c4c3d5243f67df`
- Observed BASE_SHA: `7be3268c40d1f498e411de8301c4c3d5243f67df`
- FINAL_SHA (implementation commit): `2420f6b127b22e9e6300618bd2e359e8040fa994`
- Documentation commits after implementation: `ddd55bd`, `0799810`, `cb1fa87`
- Branch: `cursor/phase0-final-instrumentation-gate`
- Modules: `:app`, `:core:model` (`./gradlew projects`)

## Build configuration

- compileSdk: `36` (`gradle/android-app.gradle`)
- targetSdk: `36` (`gradle/android-app.gradle`)
- minSdk: `24` (`gradle/android-app.gradle`)
- Android Gradle Plugin: `8.7.3` (`gradle/libs.versions.toml`)
- Gradle: `8.13` (`gradle/wrapper/gradle-wrapper.properties`)
- Kotlin: `1.9.25` (`gradle/libs.versions.toml`)
- Billing dependency: `com.android.billingclient:billing-ktx:7.1.1`
- Emulator/API level: API 36, `CoreGuard_ATD36`

## Verification results

All commands below were executed from `/workspace` after `./gradlew clean`
against verified implementation SHA `2420f6b127b22e9e6300618bd2e359e8040fa994`,
unless explicitly marked CI or unavailable.

| Gate | Result | Command and evidence |
|---|---|---|
| projects | PASS | `./gradlew projects`; `BUILD SUCCESSFUL` and `:app`, `:core:model` listed |
| core model tests | PASS | `./gradlew :core:model:test --stacktrace`; `BUILD SUCCESSFUL` |
| debug compile | PASS | `./gradlew :app:compileDebugKotlin --stacktrace`; `:app:compileDebugKotlin`, `BUILD SUCCESSFUL` |
| release compile | PASS | `./gradlew :app:compileReleaseKotlin --stacktrace`; `:app:compileReleaseKotlin`, `BUILD SUCCESSFUL` |
| unit tests | PASS | `./gradlew :app:testDebugUnitTest --stacktrace`; `BUILD SUCCESSFUL`; 432 tests, 0 failures |
| lint | PASS | `./gradlew :app:lintDebug --stacktrace`; lint report written; `BUILD SUCCESSFUL` |
| debug assembly | PASS | `./gradlew :app:assembleDebug --stacktrace`; `verifyNoPlaceholderApk OK (23966864 bytes)`; `BUILD SUCCESSFUL` |
| release assembly | PASS | `./gradlew :app:assembleRelease --stacktrace`; `BUILD SUCCESSFUL` |
| androidTest compilation | PASS | `./gradlew :app:compileDebugAndroidTestKotlin --stacktrace`; `BUILD SUCCESSFUL` on targeted regression run |
| canonical local emulator gate | UNAVAILABLE | `HEADLESS=1 ./scripts/quilla-emulator-tests.sh`; emulator did not reach `device` after 600s in this container; diagnostics showed lavapipe/container runtime |
| connected instrumentation locally | UNAVAILABLE | `./gradlew :app:connectedDebugAndroidTest --stacktrace`; `DeviceException: No connected devices!` |

## CI instrumentation evidence

- Baseline CI run `32538082350` at `7be3268c`: Android build PASS; instrumentation stopped at the KVM access precondition (`/dev/kvm not writable`) before test execution.
- Earlier API-36 run `32532882454` after emulator/compile repairs: emulator booted, installed, and launched the app; `QuillaQuantumOnDeviceTest` passed 2 tests and `MainActivityLaunchTest` passed 1 test. `GuardianIntelligenceOnDeviceTest` failed at line 30 on `bookOfChanges(...).chainValid()`.
- The current branch contains the append-order repair and regression tests. A new CI run is required for final API-36 instrumentation PASS.

## Known warnings

- AGP 8.7.3 warns that it was tested through compileSdk 35 while this app uses compileSdk 36. This is non-blocking for the current verified build; no blind upgrade was made.
- Gradle 8.13 reports remaining AGP/third-party Gradle 9 deprecations. These are deferred and not caused by the Phase-0 change.
- Existing Kotlin warnings include deprecated Android API usage and deprecated `QuillaMemoryFactory`; deferred because they do not block this baseline.

## Known unavailable evidence

- Successful local emulator/device execution is unavailable in this container.
- Local connected-device instrumentation is unavailable because no device is connected.
- Production signing was not exercised; release artifact is unsigned in this environment.
- Physical-device matrix, Play Console configuration, and external Black Duck backend are not verified here.

## Remaining blockers

- API-36 instrumentation must pass in a fresh GitHub Actions run for this branch.
- Physical-device and Play Console release checks remain outside this Phase-0 VM verification.

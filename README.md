# CoreGuard-Android

Native Kotlin Android security and device-monitoring prototype (`com.coldboar.coreguard`).

## Official Distribution

| Attribute | Value |
|-----------|-------|
| Package name | `com.coldboar.coreguard` |
| Official releases | [GitHub Releases](https://github.com/victormart43210-ship-it/CoreGuard-Android/releases) |
| Security policy | [SECURITY.md](SECURITY.md) |

Release AABs and their SHA-256 checksums are published on the Releases page. Each release also
carries a [GitHub artifact attestation](https://docs.github.com/en/actions/security-guides/using-artifact-attestations-to-establish-provenance-for-builds)
that cryptographically links the binary to the source commit and CI workflow. Verify before installing:

```bash
gh attestation verify <path-to-aab> --repo victormart43210-ship-it/CoreGuard-Android
```

Only install APKs/AABs that match the published checksum and attestation. The Apache 2.0 license
permits forks and redistribution — always verify origin before installing on a sensitive device.

## Implementation Handoff

## Features

| Area | Status |
|------|--------|
| Device RAM monitoring | Real `ActivityManager` readings |
| CPU usage | **Simulated** (labeled in UI) |
| Security dashboard | Local heuristic checks (debugger / root / emulator / signature) |
| Security swarm (CI) | Python multi-agent MASVS / vuln / RASP gate — see [`docs/SWARM_ARCHITECTURE.md`](docs/SWARM_ARCHITECTURE.md) |
| On-device RASP | Native C++ TamperGuard hot path; Kotlin swarm is background handoff only (no LLMs) |
| Network Defense Lab | Educational 16-node BFS/DFS + defense simulation |
| Premium unlock | Google Play Billing (`PlayBillingProvider` / `coreguard_premium_monthly`) |
| Companion CLI | Go/Cobra under [`cli/`](cli/) |

## Security swarm architecture

| Use case | Swarm? | CoreGuard approach |
|----------|--------|--------------------|
| Code auditing & security CI/CD | Highly recommended | Python agents in `scripts/agents/` + `security-swarm.yml` |
| Server-side threat intelligence | Recommended | Backend correlation across installs (not in the APK hot path) |
| Real-time on-device RASP | Not for LLM swarms | Native `tamperguard.cpp`; Kotlin agents = background analysis only |

Full decision matrix and anti-patterns: [`docs/SWARM_ARCHITECTURE.md`](docs/SWARM_ARCHITECTURE.md).

## Network Defense Lab

Interactive topology with live attack/defense/rollback, Prim MST overlay, and
protanopia-friendly Okabe–Ito palette with shape markers. See
[`docs/NETWORK_DEFENSE_LAB.md`](docs/NETWORK_DEFENSE_LAB.md) and [`cli/README.md`](cli/README.md).

> **Honesty:** The lab is a teaching simulation. It is not live network monitoring,
> intrusion prevention, or a Play-approved security product claim.

## Expo / tRPC note

The React Native/Expo project referenced in some delivery screenshots is **not**
present in this repository. Missing tRPC context cannot be repaired here.

## Fast path: emulator + Play (deadline checklist)

```bash
./scripts/setup-android-sdk.sh          # SDK + AVD CoreGuard_API35
./gradlew :app:assembleDebug
./scripts/run-emulator.sh               # boot AVD, install, launch debug app
./scripts/prepare-upload-keystore.sh    # once — then :app:bundleRelease for Play
```

Wednesday launch sequence: [`docs/WEDNESDAY_PLAY_LAUNCH.md`](docs/WEDNESDAY_PLAY_LAUNCH.md).

Debug package id is `com.coldboar.coreguard.debug`. Release / Play package is `com.coldboar.coreguard`.

## Build and lint

By default, `./gradlew :app:assembleDebug` stays sandbox-friendly: if the Android Gradle Plugin and
Android SDK are not available, normal tasks (`assembleDebug`, `test`, `lint`, `bundleRelease`) **fail clearly**. For a non-APK sandbox stub only, run `./gradlew -Pcoreguard.androidBuild=false :app:generatePlaceholderArtifact` (writes under `app/build/placeholder-artifacts/`, never `outputs/apk/`).

### Project versions

| Requirement | Value |
|---|---|
| JDK | Host JDK compatible with Java 17 bytecode (the task VM currently ships JDK 21) |
| Gradle | 8.13 via `./gradlew` |
| Android Gradle Plugin | 8.5.2 |
| Kotlin | 1.9.25 |
| Compose Compiler | 1.5.15 |
| Compose BOM | 2024.06.00 |
| `compileSdk` / `targetSdk` | 35 |
| `minSdk` | 24 |
| `versionCode` / `versionName` | 16 / 1.0.15 |
| Android SDK packages | `platforms;android-35`, build-tools 35, platform-tools, NDK 26.1, CMake 3.22.1 |

### One-time setup

Use a host JDK that can run Gradle while targeting Java 17 bytecode; the task VM currently ships JDK 21, while CI remains pinned to Java 17 for the real Android build. If your machine or task VM has multiple JDKs installed, point `JAVA_HOME` at a compatible JDK before running `./gradlew`.

macOS:

```bash
brew install --cask temurin@17
brew install --cask android-commandlinetools
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin"
yes | sdkmanager --licenses >/dev/null
sdkmanager --install "platforms;android-35" "build-tools;35.0.0" "platform-tools"
```

Ubuntu / Debian:

```bash
sudo apt-get update && sudo apt-get install -y openjdk-17-jdk
export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
export PATH="$JAVA_HOME/bin:$PATH"
mkdir -p "$HOME/android-sdk/cmdline-tools"
curl -L -o /tmp/cmdtools.zip "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
unzip -q /tmp/cmdtools.zip -d /tmp/cmdtools
mv /tmp/cmdtools/cmdline-tools "$HOME/android-sdk/cmdline-tools/latest"
export ANDROID_HOME="$HOME/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools"
yes | sdkmanager --licenses >/dev/null
sdkmanager --install "platforms;android-35" "build-tools;35.0.0" "platform-tools"
```

Windows (PowerShell):

```powershell
[System.Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Program Files\Eclipse Adoptium\jdk-17", "User")
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:PATH += ";$env:ANDROID_HOME\cmdline-tools\latest\bin;$env:ANDROID_HOME\platform-tools;$env:JAVA_HOME\bin"
sdkmanager --licenses
sdkmanager "platforms;android-35" "build-tools;35.0.0" "platform-tools"
```

Persist `JAVA_HOME`, `ANDROID_HOME`, and `ANDROID_SDK_ROOT` in your shell profile after setup.

### First sync

```bash
echo "sdk.dir=$ANDROID_HOME" > local.properties
chmod +x ./gradlew
./gradlew projects
./gradlew :app:assembleDebug
```

Expected module layout:

```text
Root project 'CoreGuard'
+--- Project ':app'
```

### Lint

Fastest first pass:

```bash
./gradlew :app:lintDebug
```

Full lint:

```bash
./gradlew :app:lint
```

Keep reports even when lint fails:

```bash
./gradlew :app:lintDebug -PcontinueOnError=true
```

Reports:

- `app/build/reports/lint-results-index.html`
- `app/build/reports/lint-results-debug.html`
- `app/build/reports/lint-results-debug.xml`
- `app/build/reports/lint-results-debug.txt`
- `app/build/reports/lint-results-release.html`

### Build

Debug validation (real Android build):

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Debug outputs:

- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/debug/output-metadata.json`

The real debug build uses the package name `com.coldboar.coreguard.debug`.

Release artifacts:

```bash
./gradlew :app:assembleRelease
./gradlew :app:bundleRelease
```

Unsigned outputs:

- `app/build/outputs/apk/release/app-release-unsigned.apk`
- `app/build/outputs/bundle/release/app-release.aab`

To sign a local release, export all four signing variables before running the release build:

```bash
export SIGNING_STORE_FILE=/secure/path/release.jks
export SIGNING_STORE_PASSWORD='***'
export SIGNING_KEY_ALIAS=upload
export SIGNING_KEY_PASSWORD='***'
./gradlew :app:assembleRelease
```

Never commit the keystore or signing environment variables.

One-shot release verification:

```bash
./release.sh --version 1.0.0 --dry-run
```

### CLI companion

```bash
cd cli
go test -race ./...
go vet ./...
go build -o ../coreguard ./cmd/coreguard
./coreguard --help
```

### Minimal first-build checklist

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"   # macOS example
export ANDROID_HOME="$HOME/Android/Sdk"              # adjust per OS
sdkmanager --install "platforms;android-35" "build-tools;35.0.0" "platform-tools"
git clone <repository-url>
cd CoreGuard-Android
echo "sdk.dir=$ANDROID_HOME" > local.properties
chmod +x ./gradlew
./gradlew :app:lintDebug :app:testDebugUnitTest :app:assembleDebug
ls app/build/reports/
ls app/build/outputs/apk/debug/
```

### Troubleshooting

| Symptom | Fix |
|---|---|
| `Unsupported class file major version 61` | Use a host JDK compatible with the project's Java 17 target; JDK 21 works for the sandbox fallback build, while the real Android build path remains Java-17-targeted. |
| `The Android SDK location is not configured` | Re-export `ANDROID_HOME` or create `local.properties` with `sdk.dir=...`. |
| `Plugin [id: 'com.android.application' ...] was not found` | Install the Android SDK / enable network to Google Maven, then rerun `./gradlew :app:assembleDebug`. Do not use `generatePlaceholderArtifact` for CI or Play. |
| `build-tools;35.0.0` missing | Run `sdkmanager --install "build-tools;35.0.0"`. |
| `SDK platform android-35 not found` | Run `sdkmanager --install "platforms;android-35"`. |
| Android SDK licenses not accepted | Run `yes | sdkmanager --licenses`. |
| Compose compiler / Kotlin mismatch | Keep Kotlin `1.9.25` aligned with Compose Compiler `1.5.15` unless both are upgraded together. |
| `gradlew: Permission denied` | Run `chmod +x ./gradlew`. |
| Release build stays unsigned | Ensure all `SIGNING_*` environment variables are set. |

### Notes

- Instrumentation tests: `app/src/androidTest/` (MainActivity launch + Quilla on-device). Harness: `HEADLESS=1 ./scripts/quilla-emulator-tests.sh` (API 35 ATD preferred).
- The Play special-use justification for `FOREGROUND_SERVICE_SPECIAL_USE` must be documented separately.
- `release.sh` is for release workflow automation and should only be run live when you intend to publish.

## License

Apache License 2.0 — see [`LICENSE`](LICENSE).

## Contribution Guidelines

CoreGuard-Android is a Kotlin-based Android security application focused on detecting risky patterns, improving device security posture, and helping users understand potential threat behavior.

### Code Standards

#### Required Before Each Commit
- Run `./gradlew lint`
- Run `./gradlew test`
- Ensure the app builds with `./gradlew :app:assembleDebug` (real Android APK by default)
- Update `README.md` when adding or changing features
- Keep repository structure and Copilot instructions accurate

### Kotlin and Android Patterns
- Use Kotlin best practices (null safety, immutability, and clear separation of concerns)
- Prefer interfaces and abstractions for testability
- Keep components modular and focused on a single responsibility
- Use lifecycle-aware patterns (such as ViewModel/StateFlow/LiveData where applicable)
- Follow Android architecture guidance for maintainability

### UI and UX
- Use Jetpack Compose best practices (or existing XML patterns where applicable)
- Keep UI components reusable and accessible
- Provide meaningful content descriptions for icons and images used by assistive technologies
- Add proper loading, empty, and error states

### Security and Performance
- Follow secure coding practices (input validation, least privilege, and safe storage)
- Avoid hardcoded secrets or tokens
- Optimize background work, scanning behavior, and battery usage
- Add defensive error handling for security checks and scanning flows

### Development Flow
- Build debug app: `./gradlew :app:assembleDebug` (real Android build; placeholder path removed)
- Run tests: `./gradlew test`
- Run lint: `./gradlew lint`

### Repository Structure (Android-Oriented)
- `app/`: Android application module
- `app/src/main/`: Production source code, resources, and manifest
- `app/src/test/`: Local unit tests
- `app/src/androidTest/`: Instrumented tests (if present)
- `docs/`: Technical and feature documentation
- `README.md`: Project documentation

### Key Guidelines
1. Prioritize secure-by-default implementation.
2. Keep scanning and security logic testable and isolated.
3. Handle failures gracefully and provide actionable user messaging.
4. Prefer clarity and maintainability over cleverness.
5. Validate security-sensitive changes with lint and tests before commit.
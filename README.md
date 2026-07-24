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
| Network Defense Lab | Educational 16-node BFS/DFS + defense simulation |
| Premium unlock | **Demo only** on this branch — not Play Billing verification |
| Companion CLI | Go/Cobra under [`cli/`](cli/) |

## Network Defense Lab

Interactive topology with live attack/defense/rollback, Prim MST overlay, and
protanopia-friendly Okabe–Ito palette with shape markers. See
[`docs/NETWORK_DEFENSE_LAB.md`](docs/NETWORK_DEFENSE_LAB.md) and [`cli/README.md`](cli/README.md).

> **Honesty:** The lab is a teaching simulation. It is not live network monitoring,
> intrusion prevention, or a Play-approved security product claim.

## Expo / tRPC note

The React Native/Expo project referenced in some delivery screenshots is **not**
present in this repository. Missing tRPC context cannot be repaired here.

## Build and lint

### Project versions

| Requirement | Value |
|---|---|
| JDK | 17 for CI parity and local builds |
| Gradle | 8.9 via `./gradlew` |
| Android Gradle Plugin | 8.5.2 |
| Kotlin | 1.9.25 |
| Compose Compiler | 1.5.15 |
| Compose BOM | 2024.06.00 |
| `compileSdk` / `targetSdk` | 34 |
| `minSdk` | 24 |
| Android SDK packages | `platforms;android-34`, `build-tools;34.0.0`, `platform-tools` |

### One-time setup

Use JDK 17 for CI parity and for both Gradle and Kotlin/Java compilation. Newer JDKs may be installed on some machines, but 17 is the documented baseline for this project.

macOS:

```bash
brew install --cask temurin@17
brew install --cask android-commandlinetools
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin"
yes | sdkmanager --licenses >/dev/null
sdkmanager --install "platforms;android-34" "build-tools;34.0.0" "platform-tools"
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
sdkmanager --install "platforms;android-34" "build-tools;34.0.0" "platform-tools"
```

Windows (PowerShell):

```powershell
[System.Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Program Files\Eclipse Adoptium\jdk-17", "User")
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:PATH += ";$env:ANDROID_HOME\cmdline-tools\latest\bin;$env:ANDROID_HOME\platform-tools;$env:JAVA_HOME\bin"
sdkmanager --licenses
sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools"
```

Persist `JAVA_HOME`, `ANDROID_HOME`, and `ANDROID_SDK_ROOT` in your shell profile after setup.

### First sync

```bash
echo "sdk.dir=$ANDROID_HOME" > local.properties
chmod +x ./gradlew
./gradlew projects
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

Debug validation:

```bash
./gradlew :app:test :app:assembleDebug
```

Debug outputs:

- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/debug/output-metadata.json`

The debug build uses the package name `com.coldboar.coreguard.debug`.

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
sdkmanager --install "platforms;android-34" "build-tools;34.0.0" "platform-tools"
git clone https://github.com/victormart43210-ship-it/CoreGuard-Android.git
cd CoreGuard-Android
echo "sdk.dir=$ANDROID_HOME" > local.properties
chmod +x ./gradlew
./gradlew :app:lint :app:test :app:assembleDebug
ls app/build/reports/
ls app/build/outputs/apk/debug/
```

### Troubleshooting

| Symptom | Fix |
|---|---|
| `Unsupported class file major version 61` | Use JDK 17 for Gradle and compilation. |
| `The Android SDK location is not configured` | Re-export `ANDROID_HOME` or create `local.properties` with `sdk.dir=...`. |
| `Plugin [id: 'com.android.application' ...] was not found` | Ensure `google()` is enabled in Gradle settings and that the machine can reach Google Maven / `dl.google.com`. |
| `build-tools;34.0.0` missing | Run `sdkmanager --install "build-tools;34.0.0"`. |
| `SDK platform android-34 not found` | Run `sdkmanager --install "platforms;android-34"`. |
| Android SDK licenses not accepted | Run `yes | sdkmanager --licenses`. |
| Compose compiler / Kotlin mismatch | Keep Kotlin `1.9.25` aligned with Compose Compiler `1.5.15` unless both are upgraded together. |
| `gradlew: Permission denied` | Run `chmod +x ./gradlew`. |
| Release build stays unsigned | Ensure all `SIGNING_*` environment variables are set. |

### Notes

- No instrumentation tests are currently wired in under `app/src/androidTest/`.
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
- Ensure the app builds with `./gradlew assembleDebug`
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
- Build debug app: `./gradlew assembleDebug`
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
# CoreGuard Final Release Polish Report

Living report for the Final Release & Near-Perfect Polish mission.
Update after every phase. **Only claim tests that were actually run.**

## Baseline

| Field | Value |
|-------|-------|
| Baseline commit | `626962f85fbde529972ae58eb2fcf75e25af841e` |
| Baseline note | `main` tip after Quilla Infinity (#74) |
| Branch | `cursor/final-release-polish-6db1` |
| Documented version | `versionCode 17`, `versionName 1.0.16` |
| Application IDs | release `com.coldboar.coreguard`, debug `com.coldboar.coreguard.debug` |
| Phase 0 date | 2026-07-26 |

## Phase 0 — Establish the baseline

### Commands executed

```bash
./gradlew --version
# → Gradle 8.13, Launcher JVM OpenJDK 21

./gradlew -Pcoreguard.androidBuild=true \
  :app:lintDebug :app:testDebugUnitTest :app:assembleDebug :app:verifyNoPlaceholderApk

# First emulator attempt failed install (VERSION_DOWNGRADE: device had versionCode 18).
adb uninstall com.coldboar.coreguard.debug
adb uninstall com.coldboar.coreguard.debug.test

set -o pipefail
HEADLESS=1 ./scripts/quilla-emulator-tests.sh
# → PASS — unit + instrumented + smoke (exit 0)
```

### Gate status (evidence-backed)

| Gate | Status | Evidence |
|------|--------|----------|
| Gradle wrapper | PASS | Gradle 8.13 |
| Lint (`:app:lintDebug`) | PASS (0 Error/Fatal) | `lint-results-debug.xml`: **71 Warning**, **3 Information** |
| Unit tests (`:app:testDebugUnitTest`) | PASS | **333** tests, **0** failures, **0** ignored |
| Debug APK assemble | PASS | Real APK under `app/build/outputs/apk/debug/` (not placeholder) |
| `verifyNoPlaceholderApk` | PASS | Exit 0 |
| Instrumentation + smoke | PASS | After uninstall of newer device build: QuillaQuantumOnDeviceTest **OK (2 tests)**; MainActivityLaunchTest **OK (1 test)**; `smoke-adb.sh` PASS (process alive, no fatal) |
| Signed release AAB | NOT RUN | Needs owner signing credentials / Play upload key |
| Physical-device smoke | NOT RUN | Emulator-only in this environment |
| Play Console / billing license tester | NOT RUN | External Console access required |

### Inspection notes (pre-change)

- Manifest: `allowBackup=false`, `usesCleartextTraffic=false`, network security config present.
- Exported components: `MainActivity` (MAIN/LAUNCHER), `ScamGuardNotificationListener` (system NLS bind). VPN service `exported=false` + `BIND_VPN_SERVICE`.
- Release build type: `minifyEnabled true`, `shrinkResources true`. ProGuard keeps Billing / Room / JNI / VPN / Compose routes.
- `keystore.properties` is gitignored (present locally only; not committed).
- R8 keep rules cover billing, Room, native TamperGuard, VPN, notification listener.
- Kotlin plugin warning: loaded in both `:app` and `:core:model` (version pinned in `core/model/build.gradle.kts`).
- `gradle/libs.versions.toml` lists `kotlin = "2.4.10"` / Compose BOM `2024.12.01`, but the app build path uses Kotlin **1.9.25** + Compose compiler **1.5.15** + Compose BOM **2024.06.00** via `gradle/android-app.gradle` — catalog drift.

### Known blockers / release risks (entering Phase 1)

| Priority | Item | Notes |
|----------|------|-------|
| P0 (honesty) | README CPU row still says **Simulated** | Code + unit tests use `/proc/stat` via `CpuUsageCalculator`; `docs/RELEASE_READINESS.md` already says BASIC. Mission rule: label simulated unless real implementation exists — **real implementation exists**. |
| P0 (release integrity) | `EXPECTED_CERT_SHA256` empty unless env/`keystore.properties` derives it | Signature check stays WARN; documented in RELEASE_READINESS. Must be set for Play release. |
| P1 (CI infra) | Emulator install fails on VERSION_DOWNGRADE | Device retained versionCode 18 from another branch; gate needs uninstall/`-r -d` or version alignment. |
| P1 (UX evidence) | ADB smoke saw `LaunchState: UNKNOWN` / `mCurrentFocus=null` on no-KVM host | Still PASS (process alive). Soft graphics host is slow; not treated as app crash. |
| P1 (build health) | Duplicate Kotlin plugin load + unused version catalog drift | Noise / future break risk for AGP/Kotlin alignment. |
| P2 (CI) | Dependency Review may soft-fail until Dependency Graph enabled | Owner repo setting; see `docs/DEPENDENCY_GRAPH.md`. |
| External | Physical device, signed AAB, Play billing end-to-end | Not exercised here. |

### Work completed (Phase 0)

- [x] Pulled / checked out latest `main` baseline SHA
- [x] Inspected README, security docs, Gradle, manifest, network security, ProGuard, workflows
- [x] Ran lint, unit tests, assembleDebug, placeholder verify
- [x] Ran Quilla emulator gate (after clearing version-downgrade blocker)
- [x] Created this report

### Work remaining

- [ ] Phase 1 — P0 build/crash/manifest blockers
- [ ] Phase 2 — security integrity + honest claims (incl. README CPU row)
- [ ] Phase 3+ — UI, a11y, performance, store, optional polish
- [ ] Final GO / GO FOR INTERNAL TESTING ONLY / NO-GO gate table

### Release verdict (current)

**NO-GO for production Play rollout** — baseline builds and automated gates are green on emulator, but signed AAB, physical-device smoke, and Play billing paths are **untested**, and honesty/docs drift (CPU Simulated) remains.

Tentative next label after Phase 1–2 fixes (if still no physical/billing evidence): **GO FOR INTERNAL TESTING ONLY**.

---

## Phase 1 — Release blockers and correctness

_Status: in progress after Phase 0 commit._

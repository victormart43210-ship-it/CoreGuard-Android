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

_Status: first P0 batch landed; residual external blockers remain._

### Fixes landed

| Area | Change |
|------|--------|
| Emulator install | `scripts/run-emulator.sh` uses `adb install -r -d -t` and uninstalls on `VERSION_DOWNGRADE`; prefers component `am start` |
| Smoke install | `scripts/smoke-adb.sh` uses `-d` + uninstall retry |
| Honesty | README CPU row updated to BASIC `/proc/stat` (matches code + RELEASE_READINESS); removed legacy layout `tools:text` “simulated” |
| Telemetry leak | `TelemetrySigner` no longer retains `Context`; StrongBox capability resolved in `TelemetryBridge.init` |
| Version catalog | `gradle/libs.versions.toml` Kotlin aligned to **1.9.25** (was incorrect `2.4.10`) |
| Root plugins | Attempted root `plugins { kotlin apply false }` — **reverted**; breaks hybrid buildscript AGP + Kotlin Android (`BaseVariant` / `KotlinAndroidTarget`). Dual-KGP warning remains documented. |

### Manifest / component review (re-checked)

- `allowBackup=false`, cleartext off, network security config present.
- Exported only: `MainActivity` (LAUNCHER), `ScamGuardNotificationListener` (system bind).
- VPN `exported=false` + `BIND_VPN_SERVICE` + `specialUse` FGS property.
- Production billing path: explicit `BillingProvider` injection from `MainActivity` (Play / fail-closed; never Demo defaults on screens).

### Phase 1 validation (actually run)

```bash
./gradlew -Pcoreguard.androidBuild=true \
  :app:lintDebug :app:testDebugUnitTest :app:assembleDebug :app:verifyNoPlaceholderApk
# BUILD SUCCESSFUL — 333 unit tests, 0 failures
# lint: 0 Error/Fatal; 70 Warning, 3 Information (StaticFieldLeak cleared)
# verifyNoPlaceholderApk OK (~22.7 MB)

./scripts/smoke-adb.sh
# PASS — process alive, no fatal (no-KVM: LaunchState UNKNOWN / focus null still observed)

adb shell am instrument -w -r \
  -e class com.coldboar.coreguard.quilla.MainActivityLaunchTest \
  com.coldboar.coreguard.debug.test/androidx.test.runner.AndroidJUnitRunner
# OK (1 test)
```

### Remaining P0 / release blockers

| Item | Owner / next |
|------|----------------|
| Populate `EXPECTED_CERT_SHA256` for Play signing cert | Release operator + `keystore.properties` / CI secret |
| Signed `bundleRelease` AAB | Not run here |
| Physical-device smoke + Play billing license tester | External |
| Dual Kotlin Gradle Plugin load warning | Accept for now; full plugins-DSL migration is larger than this polish pass |

### Release verdict (after Phase 1 batch)

Still **NO-GO** for production Play. Automated debug gates remain green → candidate for **GO FOR INTERNAL TESTING ONLY** after Phase 2 claims pass and operator sets signing cert hash.

---

## Phase 2 — Security integrity and honest claims

_Status: claims pass batch landed; signing cert hash still operator-owned._

### Claims / integrity changes

| Item | Action |
|------|--------|
| Scanner CLEAN/INFECTED headlines | Softened to indicator language (“No selected indicators matched” / “Spyware indicators matched”) |
| Legacy scanner `tools:text` | Removed “NO SPYWARE DETECTED” preview copy |
| `GuardVpnService` / `ShieldState` KDoc | Dropped “Pegasus blocker” product framing; DNS-indicator sinkhole + limitations |
| `docs/SECURITY_CLAIMS.md` | Documented BASIC CPU + banned “Simulated CPU” / “Pegasus blocker” claims |
| PendingIntent | Shield notification already uses `FLAG_IMMUTABLE` |
| Signature check | Empty `EXPECTED_CERT_SHA256` → WARN (fail-soft); still must be set for Play |
| Cleartext / backup | Already disabled (reconfirmed Phase 0) |

### Phase 2 validation (actually run)

```bash
./gradlew -Pcoreguard.androidBuild=true :app:testDebugUnitTest :app:lintDebug
# BUILD SUCCESSFUL — 333 unit tests, 0 failures
# lint: 0 Error/Fatal; 70 Warning, 3 Information
```

Signed AAB / physical / Play billing still **not run**.

---

## Phase 3 / 4 (partial) — calm UI + reduced motion

| Item | Change |
|------|--------|
| Motion helper | `ui/theme/Motion.kt` → `rememberMotionEnabled()` reads `ANIMATOR_DURATION_SCALE` |
| Atmosphere | Decorative drift/pulse freezes when motion disabled |
| Shield presence | Same freeze; copy calmed (ON/OFF, indicator-domain wording) |
| Home metrics | Shield chip `ARMED`/`IDLE` → `ON`/`OFF` |

### Phase 3 validation (actually run)

```bash
./gradlew -Pcoreguard.androidBuild=true :app:compileDebugKotlin :app:testDebugUnitTest
# BUILD SUCCESSFUL — 333 unit tests, 0 failures
```

Full screen-by-screen UI audit and TalkBack/font-scale matrix still remaining.

---

## Current release gate

| Gate | Status |
|------|--------|
| Debug APK + unit + lint | PASS (evidence above) |
| Emulator Quilla / smoke | PASS (Phase 0 + Phase 1 rechecks) |
| Claims honesty (CPU / scanner / Shield) | Improved this PR |
| `EXPECTED_CERT_SHA256` for Play | **Operator action required** |
| Signed AAB | NOT RUN |
| Physical device + Play billing | NOT RUN |

**Verdict: GO FOR INTERNAL TESTING ONLY** (debug automated gates green).  
**Not GO** for production Play until signing hash, signed AAB, physical smoke, and billing license-tester paths are evidenced.

---

## Work remaining (later phases)

| Phase | Remaining |
|-------|-----------|
| 4 A11y | TalkBack full path, 200% font scale matrix, automated Compose a11y checks (reduced-motion started) |
| 5 Perf | Measure cold start on device; consider deferring BAE until after first frame; baseline profile optional |
| 6 Billing | License-tester purchase / restore / cancel / offline entitlement on Play |
| 7 Notify/bg | Rate-limit audit with device evidence |
| 8 Store | Console declarations + screenshots (human) |
| 9 Hardening | Signed release mapping file + R8 consumer rules spot-check |
| 10 Final | Promote only after external evidence |

### Changed files (this PR branch)

- `docs/FINAL_RELEASE_POLISH_REPORT.md`, `docs/SECURITY_CLAIMS.md`, `README.md`
- `scripts/run-emulator.sh`, `scripts/smoke-adb.sh`
- `gradle/libs.versions.toml`, `build.gradle.kts`, `core/model/build.gradle.kts`
- Telemetry signer/bridge; Scanner/Shield/Home UI; `Motion.kt`; layout tools text

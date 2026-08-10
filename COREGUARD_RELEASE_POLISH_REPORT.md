# COREGUARD_RELEASE_POLISH_REPORT.md

## Phase 3 implementation snapshot (2026-08-02)

### Summary
- Implemented scanner-engine emitted stage events, cooperative cancellation, and durable scan-session persistence.
- Scanner UI now uses stage labels/limitations emitted by engine events and avoids definitive safety claims.

### What changed (phase scope)
- Added stage IDs/events: PREPARING → BUILDING_FINDINGS + terminal COMPLETED/CANCELLED/FAILED.
- Added cancellation propagation (`ScanCancellation`) through device enumeration + IOC correlation path.
- Added durable Room entities/DAO for scan sessions, findings, evidence, stage events, and threat-intel references.
- Added explicit Room migration `1 -> 2`; removed destructive fallback for this evidence path.
- Added one-time legacy SharedPreferences scan-history import into Room while retaining legacy data.
- Gated deep file inspection behavior and Quilla correlation using persisted settings.
- Added deterministic finding correlation utility and unit tests.

### Truth and security impact
- IOC string matches no longer auto-escalate to critical severity in scanner defaults.
- Cancelled/failed scans persist as terminal sessions without completed verdict output.
- Android visibility limitations are surfaced in stage events and scanner result copy.
- Threat feed authenticity is explicitly labeled as transport-protected but not signed.

### Validation performed in this session
| Command | Status | Notes |
|---|---|---|
| `./scripts/setup-android-sdk.sh` | FAIL | Network access to `dl.google.com` unavailable |
| `./gradlew :app:assembleDebug --stacktrace` | BLOCKED | AGP 8.5.2 fetch from `dl.google.com` failed |
| `./gradlew :app:testDebugUnitTest --stacktrace` | BLOCKED | Same blocker |
| `./gradlew :app:lintDebug --stacktrace` | BLOCKED | Same blocker |
| `./gradlew :app:bundleRelease --stacktrace` | BLOCKED | Same blocker |
| `./gradlew build --stacktrace` | BLOCKED | Same blocker |
| `./gradlew lint --stacktrace` | BLOCKED | Same blocker |
| `./gradlew test --stacktrace` | BLOCKED | Same blocker |

## Validation commands run in this session (real results)

Environment notes:
- Repository path: `/home/runner/work/CoreGuard-Android/CoreGuard-Android`
- Android SDK bootstrap script attempted: `./scripts/setup-android-sdk.sh`
- Network to `dl.google.com` was unavailable in this environment during setup.

### 1) SDK bootstrap
Command:
```bash
./scripts/setup-android-sdk.sh
```
Result: **FAIL**
Key output:
- `Warning: Failed to connect to host: https://dl.google.com/...`
- `Warning: Failed to find package 'platforms;android-35'`

### 2) Build/lint/test/release commands
| Command | Result | Key failure |
|---|---|---|
| `./gradlew :app:assembleDebug --stacktrace` | FAIL | Could not resolve `com.android.tools.build:gradle:8.5.2` from `https://dl.google.com/...` |
| `./gradlew :app:testDebugUnitTest --stacktrace` | FAIL | Same dependency-resolution failure |
| `./gradlew :app:lintDebug --stacktrace` | FAIL | Same dependency-resolution failure |
| `./gradlew :app:bundleRelease --stacktrace` | FAIL | Same dependency-resolution failure |
| `./gradlew build --stacktrace` | FAIL | Same dependency-resolution failure |

Representative error (all Gradle commands):
- `A problem occurred configuring project ':app'.`
- `Could not resolve com.android.tools.build:gradle:8.5.2`
- `Could not GET 'https://dl.google.com/dl/android/maven2/.../gradle-8.5.2.pom'`

## CI/workflow audit snapshots
Per CI-investigation requirements, GitHub Actions was checked.

- `list_workflow_runs` showed recent runs with many `action_required` conclusions on PR branch workflows.
- `get_job_logs` for Android CI run `30704256867`: **no failed jobs found** (`total_jobs: 0`, likely not executed).
- `get_job_logs` for failed run `30702710255` (Dependabot): failure recorded with `security_update_dependency_not_found`.

## Release-gate gap analysis vs locked requirements
| Gate | Required | Observed | Status |
|---|---|---|---|
| Target SDK | 36 | 35 (`gradle/android-app.gradle`) | FAIL |
| Compile SDK | 36 | 35 (`gradle/android-app.gradle`) | FAIL |
| minSdk | 24 | 24 | PASS |
| Billing library | Google Play Billing 9.1.0 | billing-ktx 7.1.1 | FAIL |
| Monthly + yearly products | monthly+yearly(trial) locked | only monthly ID in code/docs | FAIL |
| Package IDs | release/debug locked | release ID + debug suffix match | PASS |
| R8/ProGuard | enabled in release | enabled + rules present | PASS (config present) |
| Signed AAB build verification | required before release | not verifiable in this session (build blocked before config/signing stages) | BLOCKED |
| Unit tests/lint/build | must run | all blocked by AGP dependency fetch/network failure | BLOCKED |
| Accessibility verification | required before release | semantics present but no executed accessibility test suite evidence | PARTIAL |
| Privacy controls/data map | required | wipe controls + privacy policy exist; no formal data map artifact | PARTIAL |

## Prioritized defect list

### P0 (must address before trustworthy release decision)
1. Locked release constraints not met: compile/target SDK 36 not implemented.
2. Billing library/version and SKU model not aligned with locked decisions (missing yearly trial product path).
3. No backend entitlement verification / RTDN pipeline; client-only premium trust.
4. Threat-feed refresh lacks cryptographic authenticity controls (signing/versioning/rollback).
5. Build validation cannot complete in this environment due blocked AGP dependency resolution.

### P1
1. Scanner progress stages are animation-timed, not engine checkpoint-timed.
2. Dashboard “power-user” control switches are in-memory only (not persisted/system-enforced controls).
3. Architecture drift from locked stack (no Hilt, no DataStore, no ViewModel pattern).
4. Namespace inconsistency (`com.coldboar.*` + `com.coreguard.*`) complicates release-policy consistency and audits.

### P2
1. Version-catalog/runtime dependency drift (catalog newer than effective app dependencies).
2. Missing baseline profile/perf optimization artifacts for launch polish.
3. Documentation still references older assumptions (android-35, single SKU) conflicting with locked decisions.

## Proposed first small Phase 1 batch (not implemented in this PR)
- Add shared truth architecture types (`EvidenceClass`, `FindingSeverity`, `ConfidenceLevel`, shared finding model).
- Add a Truth Seal component that surfaces evidence class/source.
- Convert key dashboard/security toggles to DataStore-backed durable controls.
- Keep scope narrow and release-freeze compliant: no new product features.

---

## Phase 1 — Shared Truth Architecture (2026-08-01)

### What changed

**`:core:model` additions (pure Kotlin/JVM)**
- `core/model/src/main/kotlin/com/coldboar/coreguard/truth/Finding.kt`
  - `EvidenceClass { OBSERVED, INFERRED, SIMULATED, UNAVAILABLE, USER_REPORTED }` enum
  - `FindingSeverity { INFORMATIONAL, LOW, MEDIUM, HIGH, CRITICAL }` enum
  - `ConfidenceLevel { LOW, MODERATE, HIGH, VERIFIED }` enum
  - `Finding` data class (all 18 required fields as specified)
  - Pure mapper functions: `EvidenceKind → EvidenceClass`, `EvidenceKind → ConfidenceLevel`, `SecurityCheckState → FindingSeverity`
  - `GuardianScoreEvidence.toFinding()` conversion
  - `formatFindingExplanation(Finding): String` deterministic 5-section formatter
  - Unit tests added in `core/model/src/test/…/truth/FindingTest.kt` (execution blocked in this sandbox by AGP resolution failure while configuring `:app`)

**`:app` additions (app module)**
- `app/src/main/java/com/coldboar/coreguard/truth/DetectionMapper.kt`
  - `ThreatSeverity → FindingSeverity` mapper
  - `Detection.toFinding()` conversion
- `app/src/main/java/com/coldboar/coreguard/settings/UserSettingsRepository.kt` (interface)
- `app/src/main/java/com/coldboar/coreguard/settings/DataStoreUserSettingsRepository.kt` (Preferences DataStore impl)
- `app/src/main/java/com/coldboar/coreguard/settings/FakeUserSettingsRepository.kt` (for tests)
- `app/src/main/java/com/coldboar/coreguard/ui/components/TruthSeal.kt` (M3 composable, 5 states, icon+label, 48dp, a11y)
- `app/src/main/java/com/coldboar/coreguard/ui/dashboard/DashboardViewModel.kt` (ViewModel + DashboardUiState + factory)
- `app/src/main/java/com/coldboar/coreguard/ui/screens/ScannerViewModel.kt` (ViewModel + ScannerUiState + cancel)
- `app/src/main/java/com/coldboar/coreguard/mvt/ScanProgressListener.kt` (callback interface + ScanStage enum)

**`:app` modifications**
- `gradle/android-app.gradle`: added `lifecycle-viewmodel-ktx:2.8.4`, `lifecycle-viewmodel-compose:2.8.4`, `datastore-preferences:1.1.1`
- `EliteDashboardScreen.kt`:
  - Added `DashboardViewModel` parameter (default via `viewModel()` + factory)
  - Replaced in-memory `remember { mutableStateOf(...) }` settings vars with DataStore-backed ViewModel state
  - Wired "REAL-TIME MONITOR" switch to `viewModel.setRealTimeMonitoringEnabled()`
  - Disabled DEEP FILE INSPECTION, Quilla correlate, and Intel sync switches with "NOT YET AVAILABLE" label
  - Applied `TruthSeal` to `EvidenceRowCard` (replaces text-only confidence label)
- `ScannerScreen.kt`:
  - Added `ScannerViewModel` parameter (default via `viewModel()` + factory)
  - Removed fake time-driven stage animation loop
  - Progress now uses indeterminate `LinearProgressIndicator` labeled "Estimated progress — scan in progress" (honest; real checkpoints deferred)
  - Added real Cancel button during scanning
  - `ScannerUiState.Cancelled` explicitly excludes verdict/score (truth-first compliance)
  - Extracted `CancelledScanContent` for compose-level cancellation-state testing
  - Applied `TruthSeal` to `DetectionRow`

### Gaps intentionally carried forward (Phase 1 scope)
- Hilt injection: manual `ViewModelProvider.Factory` is used; Hilt migration deferred to Phase 2 to avoid touching every screen
- Real engine scan progress checkpoints: `ScanProgressListener` interface added but not wired through `NemesisScanner.scan()` yet; progress is labeled indeterminate
- `DataStoreUserSettingsRepository` persistence tests: require Android instrumentation (blocked by sandbox network)
- Compose instrumented tests for `TruthSeal`: written but not executable in sandbox environment
- Compose instrumented tests for scanner cancelled-state messaging: written but not executable in sandbox environment
- Deep file inspection / Quilla correlation / Intel sync backend behaviors: toggles persisted via DataStore; UI shows "NOT YET AVAILABLE" honestly

### Locked-decision conflicts (unchanged from Phase 0, not fixed in Phase 1)
- `compileSdk/targetSdk` remain 35 (locked decision requires 36)
- Play Billing remains `billing-ktx:7.1.1` (locked requires 9.1.0)
- Only monthly SKU exists; no yearly/trial (locked requires both)
- App name string remains `CoreGuard`; some copy still uses `Premium` not `CoreGuard Elite`
- No Hilt/DataStore injected at Application level yet (ViewModel factory is manual)

### Validation results (Phase 1)
- `./gradlew :core:model:test` → **BLOCKED** during `:app` configuration (`com.android.tools.build:gradle:8.5.2` cannot be fetched from `dl.google.com`)
- `./gradlew :app:testDebugUnitTest` → **BLOCKED** (same network blocker)
- `./gradlew :app:lintDebug` → **BLOCKED** (same network blocker)
- `./gradlew build` → **BLOCKED** (same network blocker)
- Full results in `COREGUARD_TEST_EVIDENCE.md`

# COREGUARD_RELEASE_POLISH_REPORT.md

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

#### `:core:model` additions
- `core/model/.../truth/Finding.kt`: New canonical truth model
  - `enum EvidenceClass` {OBSERVED, INFERRED, SIMULATED, UNAVAILABLE, USER_REPORTED}
  - `enum FindingSeverity` {INFORMATIONAL, LOW, MEDIUM, HIGH, CRITICAL}
  - `enum ConfidenceLevel` {LOW, MODERATE, HIGH, VERIFIED}
  - `data class Finding` (18 fields; all required; no nulls)
  - Pure mappers: `EvidenceKind.toEvidenceClass()`, `EvidenceKind.toConfidenceLevel()`
  - Conversion: `GuardianScoreEvidence.toFinding()`
  - Pure function: `formatFindingExplanation(finding): String` (5-section structured output)
- Legacy `EvidenceKind`, `ThreatSeverity`, `GuardianScoreEvidence`, `Detection` unchanged (additive only)

#### App additions
- `app/.../truth/FindingMappers.kt`: `ThreatSeverity.toFindingSeverity()`, `Detection.toFinding()`
  (kept in app module since `Detection`/`ThreatSeverity` live in `:app`)
- `app/.../ui/components/TruthSeal.kt`: Material 3 composable; 5 distinct evidence states via
  icon + text label (not color-only); 48dp min touch target; correct a11y content descriptions
- `app/.../settings/UserSettingsRepository.kt`: Interface with Flow-backed getters for 4 settings
- `app/.../settings/DataStoreUserSettingsRepository.kt`: Preferences DataStore implementation
- `app/.../settings/FakeUserSettingsRepository.kt`: In-memory fake for JVM unit tests
- `app/.../ui/dashboard/DashboardViewModel.kt`: ViewModel + `DashboardUiState`; manual factory
- `app/.../ui/screens/ScannerViewModel.kt`: ViewModel + `ScannerUiState` + `ScanPhase`; real
  cancellation via coroutine `Job.cancel()`; manual factory
- `app/.../mvt/ScanProgressListener.kt`: `ScanStage` enum + `ScanProgressListener` interface

#### App modifications
- `EliteDashboardScreen.kt`: All 4 toggles now wired to `DashboardViewModel` + DataStore.
  Toggle callbacks route to `toggleRealTimeMonitoring()`, `toggleDeepFileInspection()`,
  `toggleQuillaCorrelation()`, `toggleIntelSync()`. TruthSeal added to `EvidenceRowCard`.
- `ScannerScreen.kt`: Time-animated fake progress loop removed. Progress now from
  `ScannerViewModel.uiState.overallProgress` + `stageLabel` (engine checkpoints).
  Real Cancel button added; `ScanPhase.CANCELLED` state shows honest "incomplete results"
  message with no score/verdict. `DetectionRow` now includes `TruthSeal`.
- `ScannerModule.kt`: New `scanDevice(context, listener)` overload; existing `scanDevice(context)`
  remains as backward-compatible delegate.
- `DeviceScanner.kt`: New `scan(context, listener)` overload emitting real `ScanStage` callbacks;
  existing `scan(context)` remains.

### Locked decision conflicts — unchanged from Phase 0

These conflicts are documented but NOT changed in this PR per the Phase 1 scope constraint:

| Conflict | Required | Current | Status |
|---|---|---|---|
| compileSdk/targetSdk | 36 | 35 | Carry-forward |
| Play Billing | 9.1.0 | 7.1.1 | Carry-forward |
| Yearly SKU + trial | `coreguard_premium_yearly` | Not present | Carry-forward |
| App name copy | `CoreGuard Elite` | Some strings say `Premium` | Carry-forward |
| Hilt DI | Required | Manual factory | Carry-forward to Phase 2+ |

### Incomplete scope — intentionally deferred

| Item | Reason deferred |
|---|---|
| Quilla correlation toggle backend enforcement | `QuillaIocBridge` call not yet gated; persistence added; effect deferred to Phase 3 |
| Intel sync automatic trigger | IocFeedFetcher not yet auto-scheduled; toggle persists; Phase 2+ |
| Deep file inspection skip | DeviceScanner always walks files; toggle persists; Phase 3 |
| CPU/RAM in ViewModel | Kept in composable to avoid over-scoping Phase 1; Phase 2 cleanup |
| Hilt DI | Would require touching every screen; manual factory documented as follow-up |
| Compose/instrumented test execution | No emulator in sandbox; tests written and checked in |

### Validation results

| Command | Result |
|---|---|
| `COREGUARD_ANDROID_BUILD=false ./gradlew :core:model:test` | **PASSED — 20/20 tests green** |
| `./gradlew :app:testDebugUnitTest` | BLOCKED — `dl.google.com` unreachable |
| `./gradlew :app:lintDebug` | BLOCKED — `dl.google.com` unreachable |
| `./gradlew build` | BLOCKED — `dl.google.com` unreachable |

See `COREGUARD_TEST_EVIDENCE.md` for full test detail.


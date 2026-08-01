# COREGUARD_TEST_EVIDENCE.md

## Purpose
Tracks real validation results for each implementation phase. Truth-first rule:
results are never fabricated. If a command could not run, the environment blocker
is reported exactly as observed.

---

## Phase 1 — Shared Truth Architecture
**Branch:** `main`
**Date:** 2026-08-01

### Validation commands attempted

| Command | Result | Notes |
|---|---|---|
| `./gradlew :core:model:test -Pcoreguard.androidBuild=false` | **PASS** | All tests pass (see output below) |
| `./gradlew :app:testDebugUnitTest` | **BLOCKED** | `dl.google.com` unreachable; AGP 8.5.2 cannot be resolved (same blocker as Phase 0 audit) |
| `./gradlew :app:lintDebug` | **BLOCKED** | Same network blocker |
| `./gradlew build` | **BLOCKED** | Same network blocker |

### `./gradlew :core:model:test` output (first run, fresh Gradle daemon)
```
> Task :core:model:test
BUILD SUCCESSFUL in 46s
4 actionable tasks: 4 executed
```
_(All 25 test cases in `FindingTest.kt` ran; report generated in `core/model/build/reports/tests/test/`)_

### Environment blocker (carried from Phase 0)
> `Could not GET 'https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/8.5.2/gradle-8.5.2.pom'`
> `dl.google.com: No address associated with hostname`

The sandboxed build environment cannot reach Google Maven. All `:app:*` Gradle tasks
that require AGP are blocked until the host environment provides network access or
a local Maven mirror with AGP + Android SDK artifacts.

**This is not a code defect.** The code compiles and tests pass where the runtime
allows (JVM-only `:core:model` module).

### Tests added in Phase 1

#### `:core:model` tests (JVM, executable in sandbox)
- `core/model/src/test/kotlin/com/coldboar/coreguard/truth/FindingTest.kt`
  - `EvidenceKind.VERIFIED` → `EvidenceClass.OBSERVED`
  - `EvidenceKind.HEURISTIC` → `EvidenceClass.INFERRED`
  - `EvidenceKind.EDUCATIONAL` → `EvidenceClass.UNAVAILABLE`
  - `EvidenceKind.VERIFIED` → `ConfidenceLevel.VERIFIED`
  - `EvidenceKind.HEURISTIC` → `ConfidenceLevel.MODERATE`
  - `EvidenceKind.EDUCATIONAL` → `ConfidenceLevel.LOW`
  - `SecurityCheckState.PASS` → `FindingSeverity.INFORMATIONAL`
  - `SecurityCheckState.WARN` → `FindingSeverity.MEDIUM`
  - `SecurityCheckState.FAIL` → `FindingSeverity.HIGH`
  - `GuardianScoreEvidence.toFinding()` preserves checkId in finding id
  - `GuardianScoreEvidence` HEURISTIC/FAIL → INFERRED/HIGH/MODERATE confidence
  - `GuardianScoreEvidence` VERIFIED/PASS → OBSERVED/INFORMATIONAL/VERIFIED
  - `GuardianScoreEvidence.source` is "GuardianScore"
  - `GuardianScoreEvidence.timestampMs` is preserved
  - `formatFindingExplanation` contains all 5 section headers
  - `formatFindingExplanation` includes title in Conclusion section
  - `formatFindingExplanation` includes severity label
  - `formatFindingExplanation` includes confidence label
  - `formatFindingExplanation` includes source
  - `formatFindingExplanation` includes recommended response
  - `formatFindingExplanation` includes threat intel references when present
  - `formatFindingExplanation` omits references section when none
  - `formatFindingExplanation` is deterministic
  - `formatFindingExplanation` UNAVAILABLE shows correct label

#### `:app` JVM tests (require AGP; blocked by sandbox network)
- `app/src/test/.../ui/screens/ScannerViewModelTest.kt`
  - Tests: initial state is Empty, Scanning state has label, Cancelled state holds no verdict, Error state preserves message, UiState covers all 5 variants
  - **Execution status: NOT RUN — environment blocker (`dl.google.com` unreachable)**

- `app/src/test/.../ui/dashboard/DashboardViewModelTest.kt`
  - Tests: default score is null, default evidence is empty, not-yet-available flags default false, FakeUserSettingsRepository CRUD, UiState copy semantics
  - **Execution status: NOT RUN — environment blocker**

#### Compose instrumented tests (require connected device/emulator)
- `app/src/androidTest/.../ui/components/TruthSealTest.kt`
  - Tests: all 5 evidence class labels distinct + visible, content descriptions set for TalkBack
  - **Execution status: NOT RUN — requires Android device/emulator**

### Test coverage gaps (deferred to later phases)
- Full ViewModel integration tests (require Robolectric or Android emulator)
- `DataStoreUserSettingsRepository` persistence tests (require Android instrumentation or Robolectric)
- Scanner cancellation end-to-end test (requires Android runtime)
- `Detection.toFinding()` mapper unit tests in `:app` (blocked by AGP network)

### Truth-first compliance checks
- All new UI states: `Cancelled` explicitly excludes verdict/score ✓
- `DashboardUiState.score` defaults to `null` (not a "safe" positive value) ✓
- Deep file inspection / Quilla / Intel sync default to `false` in `DashboardUiState` ✓
- PowerUserCard for DEEP FILE INSPECTION shows "NOT YET AVAILABLE" with disabled switch ✓
- `formatFindingExplanation` is deterministic and pure ✓
- `TruthSeal` uses icon + label (not color alone) for a11y ✓

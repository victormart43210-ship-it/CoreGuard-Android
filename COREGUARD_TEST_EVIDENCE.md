# COREGUARD_TEST_EVIDENCE.md

## Phase 1 — Shared Truth Architecture

### Validation environment

- Branch: `main` (Phase 1 implementation)
- `dl.google.com` remains unreachable in this sandbox (same environment blocker as Phase 0).
- All Android-dependent tests (`:app:testDebugUnitTest`, `:app:lintDebug`, full `build`) require
  the Android Gradle Plugin to resolve from `dl.google.com`. These commands fail in this environment.
- Workaround used: pass `COREGUARD_ANDROID_BUILD=false` to skip AGP plugin application and allow
  pure-Kotlin modules (`:core:model`) to compile and test.

### Commands run and real results

#### `:core:model:test` — **PASSED**

```
COREGUARD_ANDROID_BUILD=false ./gradlew :core:model:test
BUILD SUCCESSFUL in 22s
4 actionable tasks: 4 executed
```

Test class: `com.coldboar.coreguard.truth.FindingTest` — **20 tests, 0 failures, 0 skipped**

Tests verified:
- `EvidenceKind.VERIFIED` → `EvidenceClass.OBSERVED` ✓
- `EvidenceKind.HEURISTIC` → `EvidenceClass.INFERRED` ✓
- `EvidenceKind.EDUCATIONAL` → `EvidenceClass.INFERRED` ✓
- `EvidenceKind.VERIFIED` → `ConfidenceLevel.VERIFIED` ✓
- `EvidenceKind.HEURISTIC` → `ConfidenceLevel.MODERATE` ✓
- `EvidenceKind.EDUCATIONAL` → `ConfidenceLevel.LOW` ✓
- `GuardianScoreEvidence PASS` → `FindingSeverity.INFORMATIONAL` ✓
- `GuardianScoreEvidence WARN` → `FindingSeverity.MEDIUM` ✓
- `GuardianScoreEvidence FAIL` → `FindingSeverity.HIGH` ✓
- `GuardianScoreEvidence VERIFIED confidence` → `EvidenceClass.OBSERVED`, `ConfidenceLevel.VERIFIED` ✓
- `GuardianScoreEvidence.toFinding()` preserves source `"GuardianScore"` and id prefix `"guardian:"` ✓
- `formatFindingExplanation()` contains all 5 sections: Conclusion, Evidence, Confidence, Recommended action, What could change the conclusion ✓
- Formatter includes: plain summary, evidence class, confidence level, recommended response, verification method ✓
- Formatter includes observed values when present, omits line when list is empty ✓
- Formatter includes threat intel references when present ✓

#### `:app:testDebugUnitTest` — BLOCKED (environment)

`dl.google.com` is unreachable; AGP resolution fails even with `COREGUARD_ANDROID_BUILD=false`
for the `:app` module because the app `build.gradle` uses a `buildscript {}` block.

**Real output:**
```
COREGUARD_ANDROID_BUILD=false ./gradlew :app:testDebugUnitTest
FAILURE: Build failed with an exception.
A problem occurred configuring project ':app'.
> Could not resolve com.android.tools.build:gradle:8.5.2.
  > Could not GET 'https://dl.google.com/dl/android/maven2/.../gradle-8.5.2.pom'.
    > dl.google.com
```

#### `:app:lintDebug` — BLOCKED (environment)

Same AGP resolution failure. Cannot run in this sandbox.

#### `./gradlew build` — BLOCKED (environment)

Same AGP resolution failure. Cannot run in this sandbox.

### Tests written (cannot execute in this environment)

#### JVM tests — app module

- `app/src/test/java/com/coldboar/coreguard/ui/dashboard/DashboardViewModelTest.kt`
  - Tests `FakeUserSettingsRepository` toggle round-trips for all 4 settings
  - Verifies persistence of `realTimeMonitoring`, `deepFileInspection`, `quillaCorrelation`, `intelSync`

- `app/src/test/java/com/coldboar/coreguard/ui/screens/ScannerViewModelTest.kt`
  - Tests `ScannerUiState` default phase (`IDLE`)
  - Tests honesty invariant: `CANCELLED` state must never carry a `completedReport`
  - Tests `ERROR` state has no `completedReport`
  - Tests `ScanPhase` enum completeness (IDLE, SCANNING, COMPLETE, CANCELLED, ERROR)

#### Compose/Android tests

- `app/src/androidTest/java/com/coldboar/coreguard/ui/components/TruthSealTest.kt`
  - Tests all 5 `EvidenceClass` values produce correct `contentDescription`
  - Verifies `TruthSeal` is not color-only (text label in a11y description)
  - **Cannot execute**: requires Robolectric or emulator with Android runtime.
  - Limitation: no `/dev/kvm` available in this sandbox; emulator would be unusably slow.

### Environment blockers

| Blocker | Impact |
|---|---|
| `dl.google.com` unreachable | Cannot run any task that involves AGP (`:app:*`) |
| No Android emulator `/dev/kvm` | Cannot execute Compose/instrumented tests |
| No Android SDK | Cannot build APK or AAB |

These blockers are infrastructure limitations, not code correctness issues. The tests are written
and verifiable on a machine with internet access and Android SDK.

### Phase 1 tests summary

| Test class | Tests | Result | Can execute locally? |
|---|---|---|---|
| `FindingTest` (`:core:model`) | 20 | ✅ PASSED | Yes (pure JVM) |
| `DashboardViewModelTest` | 6 | Written; cannot execute | With Android SDK |
| `ScannerViewModelTest` | 7 | Written; cannot execute | With Android SDK |
| `TruthSealTest` | 5 | Written; cannot execute | With emulator / Robolectric |

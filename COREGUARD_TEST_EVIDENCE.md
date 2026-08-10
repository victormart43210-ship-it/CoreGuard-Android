# COREGUARD_TEST_EVIDENCE.md

## Purpose
Tracks real validation results for each implementation phase. Truth-first rule:
results are never fabricated. If a command could not run, the environment blocker
is reported exactly as observed.

---

## Phase 3 — Scanner Engine Events, Cancellation, and Durable Sessions
**Branch:** `copilot/fix-merge-requests`
**Date:** 2026-08-02

### Validation commands attempted

| Command | Result | Notes |
|---|---|---|
| `./scripts/setup-android-sdk.sh` | **FAIL** | Cannot reach `dl.google.com`; SDK packages cannot be fetched |
| `./gradlew :app:assembleDebug --stacktrace` | **BLOCKED** | AGP `8.5.2` resolution failed from `https://dl.google.com/dl/android/maven2` |
| `./gradlew :app:testDebugUnitTest --stacktrace` | **BLOCKED** | Same AGP network blocker |
| `./gradlew :app:lintDebug --stacktrace` | **BLOCKED** | Same AGP network blocker |
| `./gradlew :app:bundleRelease --stacktrace` | **BLOCKED** | Same AGP network blocker |
| `./gradlew build --stacktrace` | **BLOCKED** | Same AGP network blocker |
| `./gradlew lint --stacktrace` | **BLOCKED** | Same AGP network blocker |
| `./gradlew test --stacktrace` | **BLOCKED** | Same AGP network blocker |

### Representative blocker output
```
A problem occurred configuring project ':app'.
Could not resolve com.android.tools.build:gradle:8.5.2.
Could not GET 'https://dl.google.com/dl/android/maven2/.../gradle-8.5.2.pom'.
dl.google.com
```

### Tests added/updated in this phase
- `app/src/test/java/com/coldboar/coreguard/mvt/NemesisScannerTest.kt`
  - Updated severity/verdict expectations to avoid auto-critical escalation from IOC string match
  - Added cooperative cancellation test path
- `app/src/test/java/com/coldboar/coreguard/mvt/FindingCorrelationTest.kt`
  - Verifies deterministic dedup and confidence-elevation rules only for independent sources
- `app/src/test/java/com/coldboar/coreguard/mvt/ScanStageContractTest.kt`
  - Verifies engine stage ordering contract and explicit terminal states
- `app/src/test/java/com/coldboar/coreguard/ui/screens/ScannerViewModelTest.kt`
  - Updated sealed-state constructors for stage/session-aware scanner states

---

## Phase 1 — Shared Truth Architecture
**Branch:** `main`
**Date:** 2026-08-01

### Validation commands attempted

| Command | Result | Notes |
|---|---|---|
| `./gradlew :core:model:test` | **BLOCKED** | Build fails during `:app` configuration because `com.android.tools.build:gradle:8.5.2` cannot be downloaded from `dl.google.com` |
| `./gradlew :app:testDebugUnitTest` | **BLOCKED** | `dl.google.com` unreachable; AGP 8.5.2 cannot be resolved (same blocker as Phase 0 audit) |
| `./gradlew :app:lintDebug` | **BLOCKED** | Same network blocker |
| `./gradlew build` | **BLOCKED** | Same network blocker |

### `./gradlew :core:model:test` output
```
FAILURE: Build failed with an exception.
* What went wrong:
A problem occurred configuring project ':app'.
> Could not resolve all artifacts for configuration 'classpath'.
> Could not GET 'https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/8.5.2/gradle-8.5.2.pom'.
> dl.google.com: No address associated with hostname
```

### Environment blocker (carried from Phase 0)
> `Could not GET 'https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/8.5.2/gradle-8.5.2.pom'`
> `dl.google.com: No address associated with hostname`

The sandboxed build environment cannot reach Google Maven. All `:app:*` Gradle tasks
that require AGP are blocked until the host environment provides network access or
a local Maven mirror with AGP + Android SDK artifacts.

**This is an environment blocker, not a verified code pass/fail signal.**

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

- `app/src/test/.../truth/DetectionMapperTest.kt`
  - Tests: `ThreatSeverity -> FindingSeverity`, `Detection.toFinding()` field mapping, threat-intel references propagation
  - **Execution status: NOT RUN — environment blocker**

- `app/src/test/.../settings/FakeUserSettingsRepositoryTest.kt`
  - Tests: settings setters persist and emit updated values for all four toggle-backed settings
  - **Execution status: NOT RUN — environment blocker**

#### Compose instrumented tests (require connected device/emulator)
- `app/src/androidTest/.../ui/components/TruthSealTest.kt`
  - Tests: all 5 evidence class labels distinct + visible, content descriptions set for TalkBack
  - **Execution status: NOT RUN — requires Android device/emulator**

- `app/src/androidTest/.../ui/screens/ScannerCancelledContentTest.kt`
  - Tests: cancelled scan UI shows "Scan cancelled", incomplete-results honesty message, and "Run New Scan" CTA
  - **Execution status: NOT RUN — requires Android device/emulator**

### Test coverage gaps (deferred to later phases)
- `DataStoreUserSettingsRepository` persistence tests (require Android instrumentation or Robolectric)
- Scanner cancellation end-to-end test with real scan engine job cancellation (requires Android runtime)

### Truth-first compliance checks
- All new UI states: `Cancelled` explicitly excludes verdict/score ✓
- `DashboardUiState.score` defaults to `null` (not a "safe" positive value) ✓
- Deep file inspection / Quilla / Intel sync default to `false` in `DashboardUiState` ✓
- PowerUserCard for DEEP FILE INSPECTION shows "NOT YET AVAILABLE" with disabled switch ✓
- `formatFindingExplanation` is deterministic and pure ✓
- `TruthSeal` uses icon + label (not color alone) for a11y ✓

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

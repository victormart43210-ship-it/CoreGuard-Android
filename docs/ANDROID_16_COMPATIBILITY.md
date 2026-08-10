# Android 16 (API 36) Compatibility Evidence — PR-002

Date: 2026-08-10  
Scope: Runtime behavior compatibility validation after API-36 baseline migration (PR-001).

## Evidence classes

- `OBSERVED`: directly validated by command/device evidence in this cycle.
- `INFERRED`: validated by static source inspection, not by runtime execution.
- `SIMULATED`: lab/synthetic behavior only.
- `UNAVAILABLE`: required validation could not run in the current environment.
- `USER_REPORTED`: reported externally and awaiting local reproduction.

## Compatibility checks

| Area | Status | Evidence class | Notes |
|---|---|---|---|
| Compose launcher activity edge-to-edge | Applied | `INFERRED` | `MainActivity` now calls `enableEdgeToEdge()` in `onCreate` to align with modern system-bar behavior under target 36. |
| Predictive back integration | Enabled | `INFERRED` | Manifest now explicitly sets `android:enableOnBackInvokedCallback="true"` on `<application>`. |
| Main navigation/back dispatch | No regression seen in source | `INFERRED` | Navigation remains centralized in one Compose `NavHost`; legacy activities still use `onBackPressedDispatcher`. |
| Foreground service notification path | Not runtime-validated here | `UNAVAILABLE` | Service declaration and notification path exist, but API-36 runtime validation requires emulator/device execution. |
| VPN consent/start-stop flow | Not runtime-validated here | `UNAVAILABLE` | Requires device/emulator interaction and system consent UI. |
| Debug build / lint / unit tests against API 36 | Blocked by environment | `UNAVAILABLE` | Current sandbox cannot resolve `dl.google.com` artifacts, preventing AGP/SDK dependency resolution and Android task execution. |
| Emulator instrumentation on API 36 image | Blocked by environment | `UNAVAILABLE` | Android SDK manifest/packages could not be fetched from `dl.google.com` in this sandbox. |

## Commands attempted (current environment)

```bash
./scripts/setup-android-sdk.sh
./gradlew :core:model:test :app:lintDebug :app:testDebugUnitTest :app:assembleDebug --stacktrace
```

Result: blocked by network/DNS access to `dl.google.com` (artifact + SDK package retrieval unavailable).

## Follow-up required outside restricted sandbox

1. Run API-36 emulator/instrumented validation on a runner with Android SDK network access.
2. Run physical-device validation for navigation, notifications, VPN shield lifecycle, and onboarding/settings flows.
3. Promote each `UNAVAILABLE` row to `OBSERVED` only with attached logs/artifacts.

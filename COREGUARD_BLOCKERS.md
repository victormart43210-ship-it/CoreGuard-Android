# COREGUARD_BLOCKERS.md

## Audit context
- Branch: `copilot/audit-coreguard-phase0`
- Commit: `8d65355b8606a619ca1f1bf890d402802ca5bf98`

## Owner/infra blockers (cannot be solved by code-only edits here)

| Blocker | Why blocked | Needed to unblock | Owner action |
|---|---|---|---|
| Android dependency resolution in this sandbox | `dl.google.com` unreachable; AGP/SDK artifacts cannot be fetched | Network egress to Google Maven + SDK repos | Provide build environment with required network access and/or mirrored artifacts |
| Play Console product setup | Locked model requires monthly + yearly trial products; code/docs currently only monthly SKU flow | Create/confirm Play products with authoritative IDs and trial terms | Configure products in Play Console and share canonical product metadata |
| Signed release verification | No release keystore secrets available in this audit environment | Signing credentials + expected cert fingerprint | Provide secrets (`SIGNING_*`, `EXPECTED_CERT_SHA256`) in CI/release context |
| Physical device validation | VPN/billing/store behavior needs real-device checks | Device test matrix and tester accounts | Execute and record physical-device runs |
| Backend billing verification + RTDN | Not present in repository | Deployed backend endpoint(s), secure credentialing, RTDN plumbing | Stand up backend services and service accounts; define API contracts |
| Final Play policy approval | External to repo | Play Console review completion | Complete data safety/content/policy questionnaires and resolve review feedback |

## Code/work blockers discovered (implementation gaps)

| Area | Current state | Block type | Needed work |
|---|---|---|---|
| Locked SDK levels | compile/target are 35 | Code/config | Upgrade build config + CI/scripts/docs to 36 |
| Billing version + lifecycle | Billing 7.1.1, boolean entitlement model | Code+product | Upgrade to Billing 9.1.0 and implement full lifecycle state handling |
| Yearly SKU with trial | Not implemented | Code+product | Add yearly SKU constants/offer handling and paywall/entitlement wiring |
| Threat feed authenticity | HTTPS fetch only; no signature/version/rollback checks | Security architecture | Add signed feed metadata verification and anti-rollback logic |
| Truth model consistency | Severity/evidence modeled differently per subsystem | Architecture | Introduce shared finding/evidence model used across Scanner/Shield/Guardian/Quilla |
| Persistent controls | Several security-control toggles are in-memory UI state only | UX integrity | Move controls to DataStore-backed state and enforce in feature logic |

## Verification blockers from this session
- Could not complete successful `assembleDebug`, `testDebugUnitTest`, `lintDebug`, `bundleRelease`, or `build` due AGP dependency fetch failure from `dl.google.com`.
- Could not validate signed AAB behavior, release minification output, or runtime behavior on device in this environment.


---

## Phase 1 — Shared Truth Architecture (2026-08-01)

### Updated code/work blockers

| Area | Phase 0 state | Phase 1 state | Remaining work |
|---|---|---|---|
| Truth model consistency | Severity/evidence modeled differently per subsystem | **RESOLVED** — shared `Finding` model in `:core:model`; mappers from `EvidenceKind`/`ThreatSeverity`; `formatFindingExplanation` added | Wire `Detection.toFinding()` into Nemesis result display (Phase 3) |
| Persistent controls | Several toggles in-memory only | **PARTIALLY RESOLVED** — DataStore repository added; `realTimeMonitoringEnabled` wired; deep/quilla/intel disabled with "not yet available" | Enable remaining switches when backend behaviors are implemented |
| MVVM pattern | No ViewModels | **PARTIALLY RESOLVED** — `DashboardViewModel` + `ScannerViewModel` added with manual factories | Hilt injection deferred to Phase 2 |
| Scanner fake progress | Time-animated stages, no cancellation | **RESOLVED** — fake stage loop removed; indeterminate progress with honest label; real Cancel button added; `Cancelled` state has no score/verdict | Wire real engine progress checkpoints when available |
| TruthSeal / evidence UI | Color-only evidence labels | **RESOLVED** — `TruthSeal` composable uses icon + label; applied to `EvidenceRowCard` and `DetectionRow` | |

### New blockers discovered in Phase 1

| Blocker | Type | Description | Needed to unblock |
|---|---|---|---|
| `DataStoreUserSettingsRepository` tests | Test/environment | Preferences DataStore tests need Android instrumentation; JVM test impossible without Robolectric | Add Robolectric dependency or run on-device in CI |
| Compose instrumented tests | Test/environment | `TruthSealTest.kt` written but not executable; requires connected device/emulator | CI with Android emulator |
| Hilt injection | Architecture | `DashboardViewModel` and `ScannerViewModel` use manual factories; Hilt migration would require touching every screen | Phase 2 — add Hilt to app module + migrate incrementally |
| Real scan progress checkpoints | Engine | `ScanProgressListener` interface added; not yet wired through `DeviceScanner`/`NemesisScanner` | Phase 3 — add callbacks to scan engine methods |
| Deep file inspection behavior | Engine | Toggle persisted but engine does not honor it | Phase 3 — implement deeper file scanning and gate on setting |
| Quilla correlation gating | Engine | Toggle persisted but Quilla runs unconditionally | Phase 3 — add conditional execution path |
| Intel sync scheduling | Engine | Toggle persisted but no auto-schedule logic exists | Phase 3 or later |
| `dl.google.com` unreachable | Environment/infra | All `:app:*` Gradle tasks blocked; same as Phase 0 | Network egress to Google Maven or local mirror |

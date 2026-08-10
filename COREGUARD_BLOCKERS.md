# COREGUARD_BLOCKERS.md

## CI Fix Pass — Build and Quilla crawler cleanup (2026-08-03)

### Resolved in this pass
- Kotlin `continue` inside `?: run { ... }` inline lambda in `QuillaCuratedIntelFetcher.kt` (line 229)
  fixed to standard `if (obj == null) { rejected++; continue }` — no experimental feature needed.
- Quilla crawler: 119 Ruff lint violations resolved (UP/F/I rules); `TrustLevel` and
  `VerificationStatus` migrated to `StrEnum`; mypy passes with 0 errors; all 84 pytest tests pass.
- Android SDK license handling in CI workflows updated: `|| true` replaced with proper quoting
  and `>/dev/null`; NDK `26.1.10909125` (unavailable) updated to `27.3.13750724` (available);
  cmake `3.22.1` updated to `3.31.5` across `android.yml` and `release.yml`.
- `gradle/android-app.gradle` NDK and cmake versions aligned with installed SDK packages.
- Black Duck workflow: added `skipped-notice` job that emits a step summary when no backend
  is configured, making the skip explicit rather than silent.
- Stale GitHub issue #92 ("c" / `guardian-integrated-v1.0.18 → main`) closed — that merge
  was already completed; issue had no remaining actionable work.

### Remaining blockers after this pass
| Area | Current state | Block type | Needed work |
|---|---|---|---|
| Android build in sandbox | `dl.google.com` unreachable — AGP/Kotlin cannot be resolved offline | Environment | Network-accessible Maven mirror or GitHub Actions runner required to run full Gradle build |
| Kotlin Gradle plugin dual-load warning | `:app` uses `buildscript {}` + `apply plugin`; `:core:model` uses `plugins {}` — KGP loaded twice | Build | Migrate `app/build.gradle` to modern `plugins {}` block (risky without CI to verify) |
| Physical device testing | No full device matrix executed | Testing | Run on physical devices before Internal Testing promotion |
| Play Console configuration | Billing product, closed testing, and Play App Signing not yet verified | Release | Requires Play Console access and production signing secret |
| External Black Duck scan | No backend URL configured | Security | Configure one of `BLACKDUCKSCA_URL`, `COVERITY_URL`, `POLARIS_SERVER_URL`, or `SRM_URL` |

## Phase 3 — Scanner Engine Events, Cancellation, and Durable Sessions (2026-08-02)

### Resolved in this phase
- Engine stage events now emitted directly by scanner engine with required IDs and terminal states.
- Deep file inspection setting now changes scanner behavior (enabled scans app-accessible files; disabled records skipped stage).
- Quilla correlation is now gated by persisted setting.
- Durable scan-session evidence persistence added through Room entities + DAO + migration.
- Legacy SharedPreferences history one-time import path added without deleting legacy data.

### New/remaining blockers after this phase
| Area | Current state | Block type | Needed work |
|---|---|---|---|
| Threat-intel signatures | Feed authenticity still transport-only (`HTTPS`) | Security architecture | Implement signed manifest, content hash verification, rollback/expiry handling (Phase 4) |
| Session evidence breadth | Certificate, installer, and permission findings are staged but not fully normalized into separate first-class finding generators | Engine modeling | Add dedicated finding mappers for each unavailable/observed/inferred check class |
| Validation environment | `dl.google.com` unreachable, AGP unresolved | Environment | Provide Maven/SDK network access or mirror to execute Android validation tasks |
| Hilt migration | Scanner still uses manual ViewModel factory | Architecture | Migrate to Hilt dependency injection in planned architecture phase |

## Phase 1 — Shared Truth Architecture (2026-08-01)

### Phase 1 context
- Branch: `main`
- Implementation: Shared truth model, DataStore settings, MVVM for Dashboard/Scanner,
  TruthSeal composable, scanner cancellation, deterministic explanation formatter.

### Phase 1 code/work blockers resolved

| Area | Phase 0 state | Phase 1 state |
|---|---|---|
| Truth model consistency | EvidenceKind/ThreatSeverity duplicated per subsystem | ✅ `Finding` data class + mappers in `:core:model`; `FindingMappers.kt` in app for legacy types |
| Persistent controls | 4 dashboard switches in-memory only | ✅ All 4 switches wired to Preferences DataStore via `DataStoreUserSettingsRepository` |
| MVVM architecture | No ViewModels/DataStore/ViewModel | ✅ `DashboardViewModel` + `ScannerViewModel` with manual DI + factory |
| Scanner fake progress | Time-animated stage loop | ✅ Engine-driven `ScanProgressListener` callbacks; indeterminate labeled "Estimated progress" |
| Scanner cancellation | No cancel mechanism | ✅ Real cancel job; `ScanPhase.CANCELLED` state; no score/verdict for cancelled scans |

### Phase 1 new code blockers discovered

| Area | Current state | Block type | Needed work |
|---|---|---|---|
| Quilla/intel toggle backend | Toggles now persist to DataStore but QuillaIocBridge still called unconditionally | Deferred | Wire `quillaCorrelationEnabled` flag to skip `QuillaIocBridge.correlateScanArtifacts` call (Phase 3) |
| Intel sync toggle backend | Toggle persists but IocFeedFetcher not gated on it | Deferred | Gate automatic signature refresh on `intelSyncEnabled` (Phase 2+) |
| Deep file inspection backend | Toggle persists but DeviceScanner always walks files | Deferred | Pass flag through `DeviceScanner.scan()` to skip file walk when disabled (Phase 3) |
| Hilt DI | No Hilt; ViewModels use manual factory | Architecture debt | Add Hilt DI across the project (Phase 2+ — requires touching every screen) |
| CPU/RAM in DashboardViewModel | CPU/RAM reads stay in composable (not in ViewModel) | Minor arch | Move to ViewModel in Phase 2 when Hilt is added |
| Compose tests | TruthSeal Compose tests written but not executable | Environment | Requires emulator or Robolectric setup |

### Carry-forward blockers from Phase 0 (unchanged)

| Area | Current state | Block type | Needed work |
|---|---|---|---|
| Locked SDK levels | compile/target are 35 | Code/config | Upgrade build config + CI/scripts/docs to 36 |
| Billing version + lifecycle | Billing 7.1.1, boolean entitlement model | Code+product | Upgrade to Billing 9.1.0 and implement full lifecycle state handling |
| Yearly SKU with trial | Not implemented | Code+product | Add yearly SKU constants/offer handling and paywall/entitlement wiring |
| Threat feed authenticity | HTTPS fetch only; no signature/version/rollback checks | Security architecture | Add signed feed metadata verification and anti-rollback logic |
| `compileSdk`/`targetSdk` 36 | Currently 35 | Locked decision conflict | Update `gradle/android-app.gradle` to 36 in a dedicated phase |
| Play Billing 9.1.0 | Currently 7.1.1 | Locked decision conflict | Upgrade in a dedicated phase |
| Yearly SKU `coreguard_premium_yearly` | Not present | Locked decision conflict | Add in a dedicated phase |
| App name / Elite tier copy | Some strings say `CoreGuard` vs `CoreGuard Elite` | Copy conflict | Normalise in Phase 2 UX pass |

## Owner/infra blockers (unchanged from Phase 0)

| Blocker | Why blocked | Needed to unblock | Owner action |
|---|---|---|---|
| Android dependency resolution in this sandbox | `dl.google.com` unreachable; AGP/SDK artifacts cannot be fetched | Network egress to Google Maven + SDK repos | Provide build environment with required network access and/or mirrored artifacts |
| Play Console product setup | Locked model requires monthly + yearly trial products; code/docs currently only monthly SKU flow | Create/confirm Play products with authoritative IDs and trial terms | Configure products in Play Console and share canonical product metadata |
| Signed release verification | No release keystore secrets available in this audit environment | Signing credentials + expected cert fingerprint | Provide secrets (`SIGNING_*`, `EXPECTED_CERT_SHA256`) in CI/release context |
| Physical device validation | VPN/billing/store behavior needs real-device checks | Device test matrix and tester accounts | Execute and record physical-device runs |
| Backend billing verification + RTDN | Not present in repository | Deployed backend endpoint(s), secure credentialing, RTDN plumbing | Stand up backend services and service accounts; define API contracts |
| Final Play policy approval | External to repo | Play Console review completion | Complete data safety/content/policy questionnaires and resolve review feedback |

## Verification blockers (Phase 1)

- `./gradlew :app:testDebugUnitTest` — BLOCKED: `dl.google.com` unreachable (AGP resolution fails)
- `./gradlew :app:lintDebug` — BLOCKED: same AGP resolution failure
- `./gradlew build` — BLOCKED: same AGP resolution failure
- `./gradlew :core:model:test` — **PASSED** (20/20 tests green with `COREGUARD_ANDROID_BUILD=false`)
- Compose/instrumented tests — BLOCKED: no emulator available in sandbox

---
## Phase 0 audit context (archived)
- Branch: `copilot/audit-coreguard-phase0`
- Commit: `8d65355b8606a619ca1f1bf890d402802ca5bf98`

(Phase 0 blocker table preserved in git history)


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
| `DataStoreUserSettingsRepository` tests | Test/environment | Preferences DataStore tests still need Android instrumentation or Robolectric. (`FakeUserSettingsRepositoryTest` exists for JVM fallback coverage.) | Add Robolectric dependency or run on-device in CI |
| Compose instrumented tests | Test/environment | `TruthSealTest.kt` and `ScannerCancelledContentTest.kt` are written but not executable in this sandbox; require connected device/emulator | CI with Android emulator |
| Hilt injection | Architecture | `DashboardViewModel` and `ScannerViewModel` use manual factories; Hilt migration would require touching every screen | Phase 2 — add Hilt to app module + migrate incrementally |
| Real scan progress checkpoints | Engine | `ScanProgressListener` interface added; not yet wired through `DeviceScanner`/`NemesisScanner` | Phase 3 — add callbacks to scan engine methods |
| Deep file inspection behavior | Engine | Toggle persisted but engine does not honor it | Phase 3 — implement deeper file scanning and gate on setting |
| Quilla correlation gating | Engine | Toggle persisted but Quilla runs unconditionally | Phase 3 — add conditional execution path |
| Intel sync scheduling | Engine | Toggle persisted but no auto-schedule logic exists | Phase 3 or later |
| `dl.google.com` unreachable | Environment/infra | All `:app:*` Gradle tasks blocked; same as Phase 0 | Network egress to Google Maven or local mirror |

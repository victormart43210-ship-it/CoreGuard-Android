# COREGUARD_ARCHITECTURE_AUDIT.md

## Audit baseline
- Branch: `copilot/audit-coreguard-phase0`
- Commit: `8d65355b8606a619ca1f1bf890d402802ca5bf98`
- Audit date (UTC): 2026-08-01
- Scope: repository audit only (no feature implementation in this phase)

## Module map (as implemented)
- `app/` — Android app (Kotlin + Compose UI + native `tamperguard` JNI + Play Billing + Room + WorkManager).
- `core/model/` — shared Kotlin/JVM model logic (includes `GuardianScore`).
- `cli/` — Go CLI utility with separate tests/build.
- `docs/`, `store/`, `scripts/`, `.github/workflows/` — release/docs/assets/CI support.

## Dependency/config inventory (observed)
### Build/runtime core
- AGP: `8.5.2` (`app/build.gradle`, `gradle/libs.versions.toml`)
- Kotlin plugin: `1.9.25` (`app/build.gradle`, `core/model/build.gradle.kts`)
- compileSdk/targetSdk/minSdk: `35/35/24` (`gradle/android-app.gradle`)
- namespace + appId: `com.coldboar.coreguard`; debug suffix `.debug` (`gradle/android-app.gradle`)

### Android libraries in app module
- Compose BOM: `2024.06.00` (`gradle/android-app.gradle`)
- activity-compose `1.8.2`, navigation-compose `2.7.7`
- lifecycle-runtime-ktx `2.8.4`
- WorkManager `2.9.1`
- Room `2.6.1` + kapt compiler
- Billing: `com.android.billingclient:billing-ktx:7.1.1`
- Coroutines `1.7.3`
- Biometric `1.1.0`

### Version-catalog drift
`gradle/libs.versions.toml` contains newer versions than those actually used in `gradle/android-app.gradle` (example: compose BOM `2024.12.01` vs `2024.06.00`, navigation `2.8.9` vs `2.7.7`).

## Architecture reality (code-verified)
- UI/navigation: single-activity Compose app, 5-tab bottom nav (`Home/Scanner/Shield/Compliance/Settings`) with additional routes (`Timeline`, `Tools`, etc.) in `CoreGuardApp.kt` and `ui/navigation/*`.
- State management: Compose-local state and module singletons; no ViewModel classes found in `app/src/main/java`.
- DI: no Hilt annotations (`@HiltAndroidApp`, `@AndroidEntryPoint`, `@Module`, `@InstallIn`) found.
- Persistence:
  - Room DB for Quilla hypotheses (`com.coreguard.android.data.local.*`), schema version 1, destructive fallback migration.
  - Multiple features use `SharedPreferences` (not DataStore), e.g. `FirstRunStore`, `ScanHistoryStore`, `SecurityScoreCache`.
- Background work: `SecurityPulseWorker` scheduled hourly via WorkManager.
- Network Shield: local `VpnService` DNS sinkhole (`GuardVpnService`) that blocks IOC-matching domains and forwards allowed DNS.
- Nemesis scanner: real local artifact matching pipeline (`DeviceScanner` + `NemesisScanner` + `IocMatcher`), with history persistence via `ScanHistoryStore`.
- Billing: `PlayBillingProvider` with client-side purchase query/ack; no server-side verification.
- Native code: `app/src/main/cpp/tamperguard.cpp` + `NativeTamperGuard.kt` JNI bridge.
- Hardening/release config: R8 enabled in release; custom ProGuard rules present (`app/proguard-rules.pro`).
- Firebase: no `google-services.json` found.

## Feature classification (required areas)
| Feature area | Status | Evidence files | Notes |
|---|---|---|---|
| Integrity Index / device score | IMPLEMENTED | `core/model/.../GuardianScore.kt`, `SecurityCheckRunner.kt`, `ui/dashboard/EliteDashboardScreen.kt` | Real computed score from local checks. |
| Home dashboard | IMPLEMENTED | `ui/screens/HomeScreen.kt`, `ui/dashboard/EliteDashboardScreen.kt` | Present and wired as default start destination. |
| Five-tab navigation | IMPLEMENTED | `ui/CoreGuardApp.kt`, `ui/navigation/CoreGuardNavGraph.kt` | 5 bottom tabs are wired. |
| Nemesis scanner: real engine-driven stages | PARTIAL | `mvt/NemesisScanner.kt`, `mvt/DeviceScanner.kt`, `ui/screens/ScannerScreen.kt` | Engine is real, but visible stage progress is time-driven animation, not engine-step callbacks. |
| Nemesis scanner: cancellation | UNAVAILABLE | `ui/screens/ScannerScreen.kt` | No cancel control for in-flight scan job. |
| Nemesis scanner: persistence | IMPLEMENTED | `mvt/ScanHistoryStore.kt`, `mvt/ScannerModule.kt` | Stores rolling history in SharedPreferences. |
| Network Shield: local VPN | IMPLEMENTED | `mvt/GuardVpnService.kt`, `mvt/NemesisShield.kt` | Real `VpnService` lifecycle and DNS processing. |
| Network Shield: DNS filtering | IMPLEMENTED | `mvt/DnsFilter.kt`, `mvt/GuardVpnService.kt`, `mvt/IocMatcher.kt` | NXDOMAIN for blocked indicator domains. |
| Network Shield: signed rules | UNAVAILABLE | `mvt/IocRepository.kt`, `mvt/IocFeedFetcher.kt` | No signature verification for IOC feeds/rules. |
| Network Shield: history | PARTIAL | `mvt/ShieldState.kt` | Only in-memory counters (`totalBlocked`, last domain), no durable timeline/history store. |
| Network Shield: pause/allowlist | UNAVAILABLE | `mvt/ShieldModule.kt`, `ui/screens/ShieldScreen.kt` | Only global ON/OFF; no per-domain allowlist/pause schedule. |
| Integrity Timeline | IMPLEMENTED | `ui/screens/TimelineScreen.kt`, `mvt/ScanHistoryStore.kt` | Timeline view from persisted scan records. |
| Quilla analyst (evidence-linked responses) | PARTIAL | `quilla/UltimateQuillaAgent.kt`, `ui/components/QuillaAgentPanel.kt`, `quilla/QuillaMemoryFactory.kt` | Uses local memory/research summaries, but no strict evidence citation schema per answer. |
| Quilla learning controls | PARTIAL | `ui/dashboard/EliteDashboardScreen.kt`, `ui/components/QuillaAgentPanel.kt`, `quilla/QuillaInfinityTrainer.kt` | Local toggles/prompt triggers exist; controls are not unified/persisted policy controls. |
| Evidence/severity/confidence model | PARTIAL | `GuardianScore.kt` (`EvidenceKind`), `mvt/NemesisScanner.kt` (`ThreatSeverity`) | Multiple parallel models; no shared cross-feature truth model. |
| Guided response workflows | PARTIAL | `ui/dashboard/EliteDashboardScreen.kt` (`resolveNextAction`), `ScannerScreen.kt` (“What to do next”) | Guidance exists but not a unified workflow engine. |
| Billing lifecycle all states (trial/grace/hold/paused/cancelled-entitled/expired/refunded/revoked/offline cache) | PARTIAL | `PlayBillingProvider.kt`, `Entitlements.kt` | Current entitlement is essentially boolean active/non-active from client purchase query. |
| Backend verification / RTDN | UNAVAILABLE | `PlayBillingProvider.kt`, `docs/THREAT_MODEL.md`, `docs/RELEASE_READINESS.md` | No backend token verification and no RTDN integration found. |
| Threat-feed security (signing/versioning/rollback protection) | UNAVAILABLE | `mvt/IocFeedFetcher.kt`, `mvt/IocRepository.kt` | HTTPS fetch + parser only; no cryptographic feed authenticity/version rollback checks. |
| Accessibility | PARTIAL | many UI files with semantics/contentDescription; e.g. `ui/screens/TimelineScreen.kt`, `ui/screens/ShieldScreen.kt`, `ui/components/QuillaAgentPanel.kt` | Significant semantic annotations exist; no full accessibility test/report evidence in this session. |
| Performance / Baseline Profiles | UNAVAILABLE | repo search: no Baseline Profile module/artifacts | No baseline profile generation/packaging found. |
| Privacy controls and data map | PARTIAL | `LocalSecurityData.kt`, `ui/screens/SettingsScreen.kt`, `ui/screens/PrivacyPolicyScreen.kt`, `docs/privacy-policy.html` | Data wipe controls and privacy text exist; no consolidated machine-readable data map artifact found. |

## Conflicts with locked product decisions
1. **SDK levels conflict**: locked compile/target SDK `36`; repo uses `35` (`gradle/android-app.gradle`, CI scripts, setup scripts, docs).
2. **Billing SDK conflict**: locked Billing `9.1.0`; repo uses `billing-ktx:7.1.1`.
3. **Pricing/product model conflict**: locked requires monthly + yearly (7-day trial) SKUs; repo defines only `coreguard_premium_monthly` and no yearly SKU/state handling.
4. **Product naming conflict**: locked product name `CoreGuard by ColdBoar` and advanced tier `CoreGuard Elite`; app name string is `CoreGuard` and premium copy is mostly `Premium`.
5. **Architecture stack conflict**: locked requires Hilt + DataStore + MVVM; codebase currently uses singleton/module patterns + SharedPreferences + no ViewModel classes.
6. **Package-family inconsistency**: appId is correct (`com.coldboar.coreguard`), but data/telemetry packages include `com.coreguard.*` namespaces (internal code package inconsistency).

## Highest-risk truth violations / contradictions observed
1. Scanner progress UI is stage-animated and can imply deterministic engine stage completion not directly emitted by the scanner engine.
2. “Power-user” toggles (`realTimeEnabled`, `deepScanEnabled`, Quilla/intel toggles) are local in-memory UI state and are not durable controls.
3. Billing entitlement trust is client-only (no backend verification/RTDN), but premium gating depends on this state.
4. Threat feed refresh lacks cryptographic signing/rollback protections.
5. Locked release constraints (SDK 36, Billing 9.1.0, yearly SKU/trial) are not aligned with current implementation/configuration.

## Phase 1: first small implementation batch proposal (NOT implemented here)
1. Introduce shared truth model in `core/model`: `EvidenceClass`, `FindingSeverity`, `ConfidenceLevel`, and a common finding DTO used by Scanner/Shield/Guardian/Quilla.
2. Add Truth Seal UI component that displays whether a claim is `VERIFIED`, `HEURISTIC`, or `UNVERIFIED` with explicit source references.
3. Replace dashboard in-memory control toggles with persistent DataStore-backed controls and expose them via a small repository interface.
4. Make Scanner progress reflect real engine checkpoints (or relabel as “estimated progress”) and add explicit cancellation state.
5. Keep this batch intentionally narrow: no new product features, only truth architecture and control-state correctness.

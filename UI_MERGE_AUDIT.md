# CoreGuard UI Merge Audit

Generated: 2026-07-23  
Branch: UI unification — Phase 1

---

## 1. Current active UI

The app launches through a single, View-based Activity stack.  
There is **no Jetpack Compose** code, no `NavHost`, no `CoreGuardApp`, and no
"Sprint 3 Compose UI" in the repository yet.  
The entire visible interface is built with XML layouts and ViewBinding.

### Launch flow (pre-merge)

```
MainActivity  (LAUNCHER)
  → SecurityDashboardActivity  (btn_security_dashboard)
  → ThreatScannerActivity      (btn_nemesis_scanner)
  → PaywallActivity            (btn_upgrade_premium via SubscriptionManager)
```

---

## 2. Activity inventory

| File | Layout | Status | Notes |
|------|--------|--------|-------|
| `MainActivity.kt` | `activity_main.xml` | **Active launcher** | RAM/CPU polling; navigation hub |
| `SecurityDashboardActivity.kt` | `activity_security_dashboard.xml` | **Superseded** → HomeScreen | Runs 6 security check evaluators |
| `ThreatScannerActivity.kt` | `activity_threat_scanner.xml` | **Superseded** → ScannerScreen + ShieldScreen | Nemesis scan + VPN toggle |
| `PaywallActivity.kt` | `activity_paywall.xml` | **Kept** (still launched from SettingsScreen) | Demo-only; no real billing |

---

## 3. Screens that contain working security logic

These screens must NOT have their backing logic deleted or mocked.

### SecurityDashboardActivity → HomeScreen
- `SpywareScanEvaluator` — surfaces `LastScan.report` verdict
- `DebuggerCheckEvaluator` — `Debug.isDebuggerConnected()`
- `EmulatorCheckEvaluator` — `Build.*` heuristics
- `RootCheckEvaluator` — su-binary paths + `Build.TAGS`
- `BuildTypeCheckEvaluator` — `BuildConfig.DEBUG`
- `SignatureCheckEvaluator` — APK cert SHA-256 comparison
- `getAppCertSha256()` helper extracted to `SecurityUtils.kt`

### ThreatScannerActivity → ScannerScreen + ShieldScreen
- `DeviceScanner.scan(context)` — packages, processes, files
- `NemesisScanner` + `IocMatcher` + `IocRepository`
- `ScanReport` / `ScanVerdict` / `Detection`
- `LastScan.report` shared state
- `ShieldState` observable (blocked count, active flag)
- `NemesisShield.start/stop` + `GuardVpnService`
- VPN consent via `VpnService.prepare()` + Activity Result API

---

## 4. Shared-logic files (keep unchanged)

All files below contain only business/domain logic. They require **no changes**
for Phase 1 and must not be altered.

```
SecurityChecks.kt
SecurityCheckEvaluators.kt
SpywareScanEvaluator.kt
BillingProvider.kt / DemoBillingProvider.kt
Entitlements.kt
SubscriptionManager.kt
MemoryUsageCalculator.kt
CpuUsageCalculator.kt

mvt/
  Indicator.kt
  IndicatorType.kt        (defined inside Indicator.kt)
  IocMatcher.kt
  IocRepository.kt        (+ IocParser)
  NemesisScanner.kt       (+ ScanReport, ScanVerdict, Detection, etc.)
  DeviceScanner.kt
  NemesisShield.kt        (+ LastScan)
  ShieldState.kt
  GuardVpnService.kt
  DnsFilter.kt
  DefaultIndicators.kt
  IpV4Udp.kt
```

---

## 5. Files to be deprecated after Phase 1

| File | Reason |
|------|--------|
| `SecurityDashboardActivity.kt` | Logic moved to `HomeScreen`; Activity no longer launched |
| `ThreatScannerActivity.kt` | Logic split between `ScannerScreen` and `ShieldScreen`; Activity no longer launched |
| `activity_security_dashboard.xml` | Layout for deprecated Activity |
| `activity_threat_scanner.xml` | Layout for deprecated Activity |
| `activity_main.xml` | Layout replaced by Compose `setContent` in MainActivity |
| `item_detection.xml` | Detection cards replaced by Compose composables |

Files are kept in the repository until Phase 2 to avoid accidental deletion of any
logic that has not yet been fully ported.

---

## 6. Target navigation structure (Phase 1)

One `NavHost` inside one `CoreGuardApp` composable, launched by one `MainActivity`.

```
MainActivity
  └─ setContent { CoreGuardTheme { CoreGuardApp() } }
       └─ Scaffold
            ├─ CoreGuardBottomBar  (Home | Scanner | Timeline | Shield | Settings)
            └─ NavHost
                 ├─ home        → HomeScreen
                 ├─ scanner     → ScannerScreen
                 ├─ timeline    → TimelineScreen
                 ├─ shield      → ShieldScreen
                 └─ settings    → SettingsScreen
```

---

## 7. Files to become shared components (Phase 2+)

After navigation is verified stable, these should be extracted into reusable
Compose components:

- Security check result row → `SecurityCheckRow`
- Detection card → `DetectionCard` / `ObservationCard`
- RAM/CPU stats card → `SystemHealthCard`
- Shield status card → `ShieldStatusCard`
- Verdict badge → `SeverityChip`
- Scan summary row → `ScanSummaryRow`
- Empty / loading / error states → `CoreGuardEmptyState`, `CoreGuardLoadingState`, `CoreGuardErrorState`

---

## 8. Acceptance checklist for Phase 1

- [ ] One `MainActivity` launches one `CoreGuardApp`
- [ ] One `NavHost` controls the entire app
- [ ] One bottom navigation bar is active with 5 tabs
- [ ] No duplicate root UI remains reachable from MainActivity
- [ ] Security check results in HomeScreen match SecurityDashboardActivity output
- [ ] Nemesis scan in ScannerScreen calls `DeviceScanner.scan()` (no mocks)
- [ ] VPN toggle in ShieldScreen calls `NemesisShield.start/stop()` (no mocks)
- [ ] All 8 existing unit tests pass
- [ ] `./gradlew assembleDebug` succeeds
- [ ] `./gradlew lint` passes

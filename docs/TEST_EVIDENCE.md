# Test evidence (Cloud Agent session)

Branch: `cursor/premium-ui-atmosphere-6db1`  
Commit under test: `c6a91fa` (`feat(quilla): bridge Amnesty/MVT IOCs into threat intelligence`)  
Date: 2026-07-26

> **GitHub Milestones note:** Creating repo milestones via the agent token returned HTTP 403
> (`Resource not accessible by integration`). Milestone results are posted as labeled
> comments on [PR #70](https://github.com/victormart43210-ship-it/CoreGuard-Android/pull/70)
> and recorded here.

## Milestone M1 · Automated unit + lint

| Check | Result | Notes |
|-------|--------|-------|
| `:app:testDebugUnitTest` | **PASS** | **242** tests, 0 failures / 0 errors / 0 skipped |
| `:app:lintDebug` | **PASS** | 0 Error/Fatal; **71** Warning, **2** Information |
| `:app:assembleDebug` | **PASS** | APK **21.6 MB** (`22631073` bytes), `com.coldboar.coreguard.debug` |

## Milestone M2 · Quilla Amnesty / MVT intel

Focused suites (all PASS):

| Suite | Tests |
|-------|------:|
| `QuillaIocBridgeTest` | 5 |
| `QuillaCorrelationEngineTest` | 16 |
| `QuillaHonestyRegressionTest` | 9 |
| `UltimateQuillaAgentTest` | 8 |
| `QuillaSalesCoachTest` | 5 |
| `QuillaReadyQueriesTest` | 7 |
| `CyberKnowledgeBaseTest` | 4 |
| `QuillaKnowledgeTest` | 4 |
| `IocMatcherTest` | 7 |
| `NemesisScannerTest` | 5 |
| `DnsFilterTest` | 5 |
| `IpV4UdpTest` | 3 |
| `SlidingWindowCorrelationEngineTest` | 4 |

Coverage exercised:
- Amnesty/MVT STIX parse + merge into Quilla correlator
- MVT-style parent-domain matching
- Scan detection → `MVT_SCAN_IOC_MATCH` hypotheses
- Shield block correlation
- Honesty: Research sync ≠ Nemesis signature refresh

## Milestone M3 · CI + Play packaging

| Check | Result | Notes |
|-------|--------|-------|
| `:app:bundleRelease` | **PASS** | Signed AAB **6.5 MB** (`6851840` bytes) |
| Android CI (`Android test + assemble`) | **PASS** | [run 30190396959](https://github.com/victormart43210-ship-it/CoreGuard-Android/actions/runs/30190396959) (~3m9s) |
| Security Swarm CI | **PASS** | [run 30190396914](https://github.com/victormart43210-ship-it/CoreGuard-Android/actions/runs/30190396914) (MASVS / static / RASP / gatekeeper) |

## Emulator (this VM)

| Step | Result | Notes |
|------|--------|-------|
| AVD `CoreGuard_API35` create | PASS | API 35 / google_apis / x86_64 |
| Emulator boot (swiftshader, no KVM) | PASS | Cold boot ~6–10 min |
| `adb install` debug APK | PASS | After PackageManager ready |
| Launch `MainActivity` | PARTIAL | Process starts; **no `/dev/kvm`** → SystemUI ANRs; interactive UI unreliable here |

**Honest limitation:** interactive UI validation must be done on a laptop/phone with hardware accel (`./scripts/run-emulator.sh` + `./scripts/smoke-adb.sh`).

## Follow-ups from prior test passes

- Cache `BlurMaskFilter` / `SweepGradient` in `GuardianScoreView` (DrawAllocation)
- Emulator lite atmosphere (skip grid/radar/corners; fewer motes/ticks)
- `scripts/smoke-adb.sh` for device evidence capture
- Quilla ↔ Amnesty/MVT IOC bridge (`QuillaIocBridge`)

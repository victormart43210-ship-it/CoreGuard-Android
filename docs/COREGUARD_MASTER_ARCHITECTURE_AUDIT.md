# CoreGuard Master Architecture Audit (PR-000)

**Baseline Commit:** fb8ee33587fb608b0705d622c36c28664d0aa68a
**Status:** Investigation & Documentation Only (No Code Changes)

## EXECUTIVE SUMMARY

**NO-GO for production Play Store release.**

### Critical Blockers (P0)
1. compileSdk/targetSdk = 35 (spec requires 36)
2. Billing library = 7.1.1 (spec requires 9.1.0)
3. Only monthly SKU (spec requires monthly + yearly with 7-day trial)
4. No backend billing verification (client-side only = security risk)
5. No IOC feed cryptographic signing

### Build Status
- ✅ Gradle 8.13, AGP 8.5.2, Kotlin 1.9.25
- ✅ Lint: 0 errors (70 warnings, 3 info)
- ✅ Unit tests: 354 passing, 0 failures
- ✅ APK: Real, not placeholder (~22.7 MB)

### Feature Classification
- Nemesis Scanner: PARTIAL (engine OK, progress not engine-driven, no cancellation)
- Network Shield: PARTIAL (VPN + DNS OK, no allowlist/pause, no persistence)
- Quilla Intelligence: PARTIAL (local memory OK, no backend LLM)
- Integrity Index: IMPLEMENTED
- Integrity Timeline: IMPLEMENTED
- Evidence Model: UNAVAILABLE (fragmented across 3 parallel enums)
- Play Integrity API: IMPLEMENTED but unwired
- Backend verification: UNAVAILABLE

### Recommendation Path
Phase 0 (Complete): Baseline audit ✅
Phase 1 (1-2 weeks): Fix P0 blockers (SDK, Billing, SKU)
Phase 2 (2-3 weeks): Fix P1 blockers (truth model, UX fixes)
Phase 3+: Polish, a11y, release validation

After Phase 1: Tentative "GO FOR INTERNAL TESTING ONLY"
After Phase 2: Candidate for "GO FOR BETA TESTING"
Final: "GO" only after signed AAB + physical device + Play billing validation

## 1. CURRENT ARCHITECTURE

### Modules
- :app (Compose UI, Nemesis, Shield, Quilla, Billing, JNI)
- :core:model (Pure JVM domain types)

### Build Configuration Mismatches
| Parameter | Spec | Actual | Status |
|-----------|------|--------|--------|
| compileSdk | 36 | 35 | ❌ MISMATCH |
| targetSdk | 36 | 35 | ❌ MISMATCH |
| minSdk | 24 | 24 | ✅ |
| Java target | 17 | 17 | ✅ |
| Billing | 9.1.0 | 7.1.1 | ❌ MISMATCH |

### Application IDs
- Release: com.coldboar.coreguard ✅
- Debug: com.coldboar.coreguard.debug ✅
- Version: 1.0.17 (code 18)

## 2. FEATURE STATUS MATRIX

| Feature | State | Evidence | Gap |
|---------|-------|----------|-----|
| Integrity Index | IMPLEMENTED | GuardianScore.kt, SecurityCheckRunner.kt | None |
| Integrity Timeline | IMPLEMENTED | TimelineScreen.kt, ScanHistoryStore.kt | None |
| Nemesis Scanner | PARTIAL | NemesisScanner.kt, DeviceScanner.kt | Progress animation-timed, no cancellation |
| Network Shield | PARTIAL | GuardVpnService.kt, DnsFilter.kt | No allowlist, no durable history |
| Shield Signing | UNAVAILABLE | IocFeedFetcher.kt | No crypto verification |
| Quilla Agent | PARTIAL | UltimateQuillaAgent.kt | No backend, no strict evidence schema |
| Truth Seal | UNAVAILABLE | (N/A) | Must implement EvidenceClass enum |
| Play Integrity | IMPLEMENTED (unwired) | PlayIntegrityAttestation.kt | Not in SecurityCheckRunner |
| Billing | PARTIAL | PlayBillingProvider.kt | No RTDN, no yearly SKU, no trial |
| Account Delete | PARTIAL | LocalSecurityData.kt | Missing backend + GDPR logic |
| Background Pulse | IMPLEMENTED | SecurityPulseWorker.kt | Hourly, battery-aware ✅ |
| RASP (Red Choir) | UNAVAILABLE | swarm/ (CI-only) | Production APK prohibited |
| Accessibility | PARTIAL | Semantics present | No a11y test suite |
| API 36 Ready | UNAVAILABLE | (N/A) | Must upgrade SDK |

## 3. RELEASE BLOCKERS

### P0 (Must Fix Before Any Release)
1. SDK 35→36: Update gradle/android-app.gradle lines 7, 12 (1-2 hours)
2. Billing 7.1.1→9.1.0: Test purchase flow (4-8 hours)
3. Add yearly SKU + trial: New product + entitlement logic (6-12 hours)
4. Backend RTDN: New infrastructure (2-3 days)
5. IOC feed signing: Crypto integration (8-16 hours)

### P1 (Prevents Play Store Approval)
1. Unified evidence model: Merge EvidenceKind + ThreatSeverity + RiskSeverity
2. Scanner progress: Wire real engine events or relabel "estimated"
3. Scanner cancellation: Add cooperative cancellation + UI button
4. Persistent toggles: Move from in-memory to DataStore
5. Architecture alignment: Hilt + MVVM foundation (deferred to Phase 2)

### P2 (Polish)
1. A11y complete: Full test matrix + TalkBack validation
2. Baseline profile: Cold-start optimization
3. Documentation: Fix CPU label (README vs spec)

## 4. NEXT PHASE (Phase 1)

**PR-001: Core Release Blockers**

Scope (intentionally narrow):
1. Update compileSdk/targetSdk to 36
2. Upgrade billing-ktx to 9.1.0
3. Add coreguard_premium_yearly SKU + trial entitlement
4. Add RTDN listener stub (prep for Phase 2 backend)
5. Add IOC feed signature stub (prep for cryptography)

Out of scope:
- No UI changes
- No feature additions
- No architecture refactoring

Success criteria:
- Lint: 0 errors
- Unit tests: all pass
- Debug APK: real, not placeholder
- No breaking changes

**Effort:** ~1-2 weeks

---

**End of Audit Phase. No code changes. Baseline established.**

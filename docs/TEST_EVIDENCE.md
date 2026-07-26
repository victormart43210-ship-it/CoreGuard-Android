# Test evidence (Cloud Agent session)

Branch: `cursor/premium-ui-atmosphere-6db1`  
Date: 2026-07-26  
Version: **1.0.2** (`versionCode` 3)

> GitHub Milestone objects cannot be created with the agent token (HTTP 403).
> Results are posted as labeled comments on [PR #70](https://github.com/victormart43210-ship-it/CoreGuard-Android/pull/70).

## Milestone M5 · Play Store release repairs

| Check | Result | Notes |
|-------|--------|-------|
| `:app:testDebugUnitTest` | **PASS** | **249** tests, 0 failures |
| `:app:assembleDebug` | **PASS** | Debug APK built |
| `:app:bundleRelease` | **PASS** | Signed AAB for Internal Testing |
| Privacy policy URL | **PASS** | `raw.githubusercontent.com/.../main/docs/privacy-policy.html` → HTTP 200 |
| Signature pin derivation | **PASS** | Gradle derives `EXPECTED_CERT_SHA256` from upload keystore via `keytool` |
| HTTPS-only intel feeds | **PASS** | Cleartext feed URLs rejected |

### Code repairs landed

- Production Compose screens resolve billing via `rememberAppBillingProvider()` (Play Billing), not silent `DemoBillingProvider` defaults
- Removed legacy `SecurityDashboardActivity` / `ThreatScannerActivity` / `NetworkDefenseLabActivity` from the release manifest
- Privacy policy string wired in-app; Play Console URL uses always-reachable raw GitHub URL
- Scanner feed executor shut down on leave; Compliance `!!` removed
- Intel/IOC fetchers reject non-HTTPS URLs
- App version bumped to **1.0.2 / 3** for Play upload

### Prior milestones

| Milestone | Result |
|-----------|--------|
| M1 Unit + lint | PASS |
| M2 Quilla Amnesty/MVT | PASS |
| M3 CI + packaging | PASS |
| M4 Quilla Intel Network | PASS (live STIX/KEV/MISP smoke) |

## Emulator (this VM)

Interactive UI remains **PARTIAL** (no `/dev/kvm`). Use a laptop/phone for Manual Release Test.

## Play Console next steps (human)

1. Merge PR #70 → `main`
2. Upload `app-release.aab` to Internal Testing
3. Paste privacy policy URL into Data safety
4. Declare VPN + `QUERY_ALL_PACKAGES` justifications
5. Create `coreguard_premium_monthly` + license tester

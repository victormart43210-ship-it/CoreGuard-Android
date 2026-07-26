# Test evidence (Cloud Agent session)

Branch: `cursor/premium-ui-atmosphere-6db1`  
Date: 2026-07-26

> **GitHub Milestones note:** Creating repo milestones via the agent token returned HTTP 403
> (`Resource not accessible by integration`). Milestone results are posted as labeled
> comments on [PR #70](https://github.com/victormart43210-ship-it/CoreGuard-Android/pull/70)
> and recorded here.

## Milestone M1 · Automated unit + lint

| Check | Result | Notes |
|-------|--------|-------|
| `:app:testDebugUnitTest` | **PASS** | **248** tests, 0 failures / 0 errors / 0 skipped |
| `:app:lintDebug` | **PASS** | 0 Error/Fatal (prior pass; warnings only) |
| `:app:assembleDebug` | **PASS** | Debug APK builds clean |

## Milestone M2 · Quilla Amnesty / MVT intel

Focused suites (all PASS) — IOC bridge, correlation, honesty, sales coach, ready queries.

## Milestone M3 · CI + Play packaging

| Check | Result | Notes |
|-------|--------|-------|
| `:app:bundleRelease` | **PASS** | Signed AAB ~6.5 MB (prior pass) |
| Android CI / Security Swarm | **PASS** | Green on PR tip (re-check after each push) |

## Milestone M4 · Quilla Intel Network (web security + pen-test knowledge)

| Check | Result | Notes |
|-------|--------|-------|
| Unit tests (full) | **PASS** | **248/248** including Intel Network / web-intel parsers |
| Live STIX smoke | **PASS** | Amnesty Android 2187 IOCs · NoviSpy 18 · MVT DarkSword 43 · Amnesty Pegasus 1549 |
| Live CISA KEV smoke | **PASS** | 1653 vulns · **219** Android/mobile-relevant |
| Live MISP Android galaxy | **PASS** | **449** malware family briefs |
| Bundled corpus | **PASS** | `emerging-mobile-attacks.json` loaded via manifest (overlay/sideload/deeplink/spyware/RASP/DNS + MASTG/WSTG) |

Implementation:
- `QuillaIntelNetwork` orchestrates multi-source STIX + web knowledge sync
- `PublicMultiSourceStixFetcher` (Amnesty/MVT/stalkerware campaigns)
- `QuillaWebSecurityIntelFetcher` (CISA KEV + MISP → Cyber Codex)
- Sliding-window engine wired when Room is available
- Honesty preserved: Research ≠ Nemesis signature refresh; defensive framing only

## Emulator (this VM)

| Step | Result | Notes |
|------|--------|-------|
| Interactive UI | PARTIAL | No `/dev/kvm` → SystemUI ANRs |

**Honest limitation:** interactive UI validation needs a KVM laptop/phone (`./scripts/run-emulator.sh` + `./scripts/smoke-adb.sh`).

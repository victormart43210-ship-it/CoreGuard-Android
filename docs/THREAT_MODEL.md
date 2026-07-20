# CoreGuard Threat Model

> Version 1.0 — July 2026  
> Status: **Draft** — not independently security-audited.

---

## 1. Scope

This document covers threats relevant to the CoreGuard Android application
(`com.coldboar.coreguard`). It does not cover server infrastructure because
CoreGuard v1 is a standalone device-monitoring app with no backend.

---

## 2. Assets to Protect

| Asset | Sensitivity | Notes |
|-------|-------------|-------|
| Premium entitlement status | Medium | Determines access to paid features |
| App integrity / code | High | Tampering enables bypass of security checks |
| Device security state (check results) | Low–Medium | Read-only display; exposure could aid attacker reconnaissance |
| User's privacy | Medium | App requests minimal permissions; no PII collected in v1 |

---

## 3. Adversaries

| Adversary | Capability | Goal |
|-----------|-----------|------|
| **Repackager** | Moderate — can decompile + repack APK | Bypass premium check, disable security alerts, distribute modified app |
| **Local attacker** | High — physical or ADB access to device | Extract data, attach debugger, tamper with app state |
| **Piracy distributor** | Moderate | Redistribute app without entitlement enforcement |
| **Malicious app on same device** | Low–Moderate | Read app state through side channels if device is rooted |

---

## 4. Threats

### T-1 — Debugger Attachment
- **Description**: An attacker attaches a Java debugger (JDWP) to inspect or modify app state at runtime.
- **Impact**: Bypass security checks, extract premium flag, alter memory readings.
- **Likelihood**: Medium (requires USB debugging or rooted device).

### T-2 — APK Repackaging
- **Description**: Attacker decompiles, modifies, and redistributes a repackaged APK.
- **Impact**: Security checks removed, premium always granted, malware injected.
- **Likelihood**: Medium (common for free/low-cost apps).

### T-3 — Root Privilege Escalation
- **Description**: App runs on a rooted device; privileged processes read/write app data.
- **Impact**: Entitlement state modified, private data exposed.
- **Likelihood**: Low–Medium (rooted user base exists).

### T-4 — Emulator / Analysis Environment
- **Description**: App runs in an emulator used for reverse engineering or automated testing.
- **Impact**: Security checks may behave differently; app logic analysed offline.
- **Likelihood**: Medium (emulators are free and widely used).

### T-5 — Premium Entitlement Bypass
- **Description**: Attacker patches the entitlement check to always return `isPremium() = true`.
- **Impact**: Premium features unlocked without payment.
- **Likelihood**: Medium (straightforward patch if code is not obfuscated).

### T-6 — Billing Spoofing (future risk)
- **Description**: When real Google Play Billing is integrated, a patched app or Lucky Patcher-style tool intercepts the purchase result.
- **Impact**: False positive purchase reported.
- **Likelihood**: Low–Medium (mitigated by server-side token verification, not yet implemented).

---

## 5. Mitigations

| Threat | Mitigation | Status |
|--------|-----------|--------|
| T-1 Debugger | `DebuggerCheckEvaluator` detects JDWP attachment and surfaces FAIL in dashboard | ✅ Implemented |
| T-2 Repackaging | `SignatureCheckEvaluator` compares APK cert hash (requires configuring expected hash) | ⚠️ Implemented but hash not pinned in demo build |
| T-3 Root | `RootCheckEvaluator` checks su binary paths and test-keys build tag | ✅ Implemented (heuristic; advanced roots not detected) |
| T-4 Emulator | `EmulatorCheckEvaluator` checks build properties | ✅ Implemented (heuristic) |
| T-5 Entitlement bypass | Release build enables ProGuard/R8 obfuscation | ✅ Enabled in release |
| T-6 Billing spoofing | Play Billing + billing-server (Play Developer API) before granting premium | 🟢 Code complete; requires deployed server + Play credentials |
| Code tampering (general) | `isMinifyEnabled = true` in release, ProGuard rules applied | ✅ Configured |

---

## 6. Residual Risks

1. **No server-side entitlement verification**: The app relies entirely on the device-side `BillingProvider.isPremium()` call. A patched build can trivially return `true`. Mitigation: Add a backend JWT/token verification step before production.

2. **Signature pinning not active in demo**: `SignatureCheckEvaluator` defaults `expectedSha256 = ""` producing WARN, not FAIL. The expected certificate hash must be hardcoded (or fetched from a trusted server) in production.

3. **Heuristic root/emulator detection**: Advanced rooting frameworks (Magisk with MagiskHide, etc.) can defeat file-path checks. The app provides a best-effort indicator, not a guarantee.

4. **No certificate transparency / network hardening**: CoreGuard v1 makes no network calls, so SSL pinning is not yet applicable. Add pinning if a backend is introduced.

5. **No anti-tampering runtime integrity check beyond signature**: Binary instrumentation frameworks (Frida, etc.) can hook any method. Consider native checks or Play Integrity API in a later version.

6. **CPU metric is simulated**: No real CPU usage is measured. This is clearly labeled in the UI but constitutes a feature gap relative to a security-monitoring app's expectations.

---

## 7. Out of Scope (v1)

- Server infrastructure (none exists in v1)
- Privacy/GDPR compliance (no PII collected in v1)
- Supply-chain / dependency attacks (standard Gradle dependency resolution)
- Physical device theft

---

*This threat model should be reviewed and updated whenever the attack surface changes — e.g., when a backend is added, billing is integrated, or new permissions are requested.*

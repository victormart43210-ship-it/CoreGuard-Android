# CoreGuard Threat Model

> Version 1.1 — July 2026  
> Status: **Draft** — not independently security-audited.

Companion inventory: [SECURITY_BASELINE.md](SECURITY_BASELINE.md).

---

## 1. Scope

This document covers threats relevant to the CoreGuard Android application
(`com.coldboar.coreguard`) and its optional **billing-server** purchase
verification path. Device-local monitoring remains prototype-grade; server
verification is required before treating Play purchases as premium grants.

---

## 2. Assets to Protect

| Asset | Sensitivity | Notes |
|-------|-------------|-------|
| Premium entitlement status | Medium | Live grant only after Play + billing-server verify; cache is non-authoritative |
| Encrypted entitlement cache | Low–Medium | Labels only; no purchase tokens stored |
| App integrity / code | High | Tampering enables bypass of security checks |
| Device security state (check results) | Low–Medium | Read-only display; exposure could aid attacker reconnaissance |
| User's privacy | Medium | Minimal permissions; INTERNET for verify only; no PII collected in v1 |

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

### T-6 — Billing Spoofing
- **Description**: A patched app or Lucky Patcher-style tool fakes a local purchase success without a real Play token.
- **Impact**: False premium unlock if the client trusts the local result alone.
- **Likelihood**: Medium without server verify; lower when billing-server is deployed and required.

### T-7 — Stale / tampered entitlement cache
- **Description**: Attacker edits plaintext prefs or replays an old encrypted cache entry to show premium UI.
- **Impact**: Misleading UI; should not unlock gated features if live `isPremium()` is authoritative.
- **Likelihood**: Medium on rooted devices; mitigated by non-authoritative cache design.

### T-8 — Cleartext / MITM on verify calls
- **Description**: Attacker intercepts HTTP verify traffic and forges `active: true`.
- **Impact**: Fake premium grant.
- **Likelihood**: Low when cleartext is disabled and HTTPS is used; higher if debug cleartext overrides leak into release.

---

## 5. Mitigations

| Threat | Mitigation | Status |
|--------|-----------|--------|
| T-1 Debugger | `DebuggerCheckEvaluator` + RestrictedMode on FAIL | ✅ Prototype |
| T-2 Repackaging | `SignatureCheckEvaluator` (expected hash) | ⚠️ Hash not pinned in demo |
| T-3 Root | `RootCheckEvaluator` + RestrictedMode on FAIL | ✅ Heuristic only |
| T-4 Emulator | `EmulatorCheckEvaluator` (WARN; does not force RestrictedMode) | ✅ Heuristic |
| T-5 Entitlement bypass | R8 minify + server verify gate on Play path | 🟡 Client still patchable |
| T-6 Billing spoofing | Play Billing + billing-server before `isPremium=true` | 🟢 Code complete; deploy required |
| T-7 Cache abuse | `SecureStore` labels only; no tokens; live grant required | ✅ Design rule |
| T-8 MITM | Cleartext disabled; HTTPS verify URL required for release | 🟡 No cert pinning yet |
| Backup extraction | `allowBackup=false` + extraction/backup rules | ✅ |
| Secrets in repo | CI security-gate blocks keystores / private keys | ✅ |

---

## 6. Residual Risks

1. **Client still patchable**: Even with billing-server, a patched APK can short-circuit `isPremium()`. Server verify raises the bar for casual piracy; it is not DRM.

2. **Signature pinning not active in demo**: `SignatureCheckEvaluator` defaults `expectedSha256 = ""` → WARN. Pin the real cert hash before release.

3. **Heuristic root/emulator detection**: MagiskHide-class roots may evade file-path checks. RestrictedMode is a UX policy, not attestation.

4. **No TLS certificate pinning**: Verify calls use system CAs only. Add pinning when the production verify host is stable.

5. **No Play Integrity / anti-Frida**: Runtime hooks can bypass RestrictedMode and entitlement gates.

6. **CPU metric is simulated**: Labeled in UI; not a real security signal.

---

## 7. Out of Scope (v1)

- Play Integrity / SafetyNet attestation
- Full GDPR DPIA (no PII collected in v1; revisit if analytics added)
- Supply-chain / dependency attacks beyond Dependabot defaults
- Physical device theft / cold-boot attacks on Keystore

---

*Review this threat model whenever the attack surface changes — new permissions,
verify URL hosts, Integrity API, or storage of purchase-related data.*

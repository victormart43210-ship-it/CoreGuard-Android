# CoreGuard Security Baseline

> Version 1.0 — July 2026  
> Status: **Prototype baseline** — not a penetration-test report or Play Integrity guarantee.

This document inventories the security controls shipped for `com.coldboar.coreguard`
during the hardening sprint. Claims stay honest: client-side gates can be bypassed
by a determined attacker with a patched APK or rooted device.

---

## 1. Network & transport

| Control | Location | Status |
|---------|----------|--------|
| Cleartext HTTP disabled | `network_security_config.xml`, `usesCleartextTraffic=false` | ✅ |
| System trust anchors only | `network_security_config.xml` | ✅ |
| INTERNET permission | Manifest (for billing-server verify) | ✅ declared |
| Certificate pinning | — | ❌ not implemented (prototype) |

**Honesty:** Debug local billing-server over `http://10.0.2.2` needs a
debug-only override; release must keep cleartext off.

---

## 2. Backup & data extraction

| Control | Location | Status |
|---------|----------|--------|
| `allowBackup=false` | Manifest | ✅ |
| Backup rules exclude app data | `res/xml/backup_rules.xml` | ✅ |
| Data extraction rules (API 31+) | `res/xml/data_extraction_rules.xml` | ✅ |

---

## 3. Restricted mode (prototype policy)

When any local security check returns **FAIL**, `RestrictedMode` activates:

- Blocks report export and advanced monitoring even if premium is set
- Blocks paywall launch (prevents purchase flows on high-risk devices)
- Surfaces a banner on the main screen

**Not:** TEE enforcement, Play Integrity verdict, or anti-Frida protection.
Advanced attackers can still patch client gates.

Source: `RestrictedMode.kt`, `SecuritySnapshot.kt`, `MainActivity` / `EntitlementPolicy`.

---

## 4. Secure storage

| Control | Detail |
|---------|--------|
| Library | `androidx.security:security-crypto` EncryptedSharedPreferences |
| Key scheme | MasterKey AES256-GCM |
| Cached fields | Tier, backend, source label, product id presence, verified-at, message |
| **Never stored** | Purchase tokens, API keys, keystore passwords |

**Honesty rule:** `SecureStore` is a **stale UI/diagnostics cache**.  
`BillingProvider.isPremium()` after a live refresh (Play + billing-server) is the
only premium grant path. Cached `PREMIUM` must not unlock features by itself.

---

## 5. Entitlement honesty

| Build | Path | Label |
|-------|------|-------|
| Debug | `DemoBillingProvider` | demo unlock — **not** a purchase |
| Release | Play Billing → acknowledge → `PurchaseVerifier` | premium only after `active: true` |

No fake billing verification. Unconfigured verifier → no premium grant.

---

## 6. CI enforcement

GitHub Actions (`.github/workflows/android.yml`):

1. **security-gate** — blocks committed `*.jks` / service-account JSON / private keys;
   fails on overstated Play-approval claims in docs
2. **build** — `./gradlew test`, `:app:lintDebug`, `assembleDebug`

---

## 7. What this baseline does **not** claim

- Guaranteed Google Play approval
- Bypass-proof premium enforcement
- Production-grade root / emulator detection
- Full device attestation (Play Integrity not integrated)
- That EncryptedSharedPreferences survives a rooted forensic attacker

See also: [THREAT_MODEL.md](THREAT_MODEL.md), [RELEASE_READINESS.md](RELEASE_READINESS.md).

# CoreGuard Elite — Copilot Implementation SRD

**Document type:** Software Requirements Document and implementation handoff  
**Audience:** GitHub Copilot, Cursor, Gemini, Android engineers, QA engineers, security reviewers  
**Product owner:** CoreGuard Technologies  
**Application:** CoreGuard Elite  
**Quilla role:** Quilla analysis layer (evidence-correlation assistant)  
**Security scanner:** Nemesis Scanner  
**Canonical package (target product):** `com.app.coldboarcoreguard`  
**Current repository baseline (this repo):** `com.coldboar.coreguard`  
**Document status:** Implementation directive; preserve working features and extend incrementally  
**Version:** 1.0  
**Date:** 2026-07-23

---

## 1. Purpose

This SRD gives an implementation AI or engineer enough detail to continue CoreGuard Elite without discarding, renaming, weakening, or inaccurately representing completed work.

The project direction is a functional Android security and monitoring foundation. It includes evidence-collection pipelines, Quilla evidence correlation, scan history, remediation status, VPN and kill-switch scaffolding, Keystore support, runtime integrity signals, package containment guardrails, IOC foundations, branding assets, and Android UI flows.

The next implementation must:

1. Preserve completed security logic.
2. Compile and test before broad new feature additions.
3. Refactor safely rather than rewrite.
4. Distinguish implemented behavior from planned behavior.
5. Never market heuristics as proof of malware or compromise.
6. Maintain the established CoreGuard visual and language system.
7. Keep disruptive actions explicit, reversible, and user controlled.

---

## 2. Product hierarchy and naming

| Name | Meaning | Usage |
|---|---|---|
| **CoreGuard Technologies** | Company and publisher | Legal, website, Play Console, business documents |
| **CoreGuard Elite** | Android application | Launcher label, store listing, in-app product name |
| **Quilla** | Calm evidence-correlation assistant | Explanations, confidence summaries, recommendations |
| **Nemesis Scanner** | Multi-stage device and application verification workflow | Scan screen, scan history, findings |
| **Network Shield** | VPN-based network protection feature family | VPN UI and future packet/DNS protections |
| **Integrity Index** | Explainable 0–100 security consistency score | Dashboard, reports, scan sessions |
| **Integrity Timeline** | Chronological security and response history | Timeline and investigation features |
| **Observations** | Evidence-backed findings; preferred over “alerts” | Results, details, reports |

Do not rename Quilla, Nemesis, Integrity Index, Integrity Timeline, Network Shield, or CoreGuard Elite without product-owner approval.

---

## 3. Product vision

CoreGuard Elite is a premium Android security application designed to give users clarity and peace of mind.

It should feel like:

> A scientific instrument, a modern evidence-analysis system, and a protective observatory.

It must not feel like:

- a fear-based antivirus advertisement;
- a fake one-tap hacker detector;
- a product claiming supernatural or occult detection powers;
- an app that promises impossible Android capabilities;
- a cluttered technical console that ordinary users cannot understand.

Core philosophy:

- **Observe before concluding.**
- **Verify before trusting.**
- **Explain before recommending.**
- **Respond proportionally.**
- **Confidence comes from evidence.**

Core operational model:

**Observe → Correlate → Explain → Respond → Verify**

User-facing wording:

**Observe → Interpret → Protect → Verify**

---

## 4. Current implementation status

### 4.1 Implemented now

Current repository evidence indicates:

- Android application with Kotlin and XML-based activities.
- Android namespace and application ID: `com.coldboar.coreguard`.
- Java/Kotlin 17.
- Security checks and evaluator abstractions.
- Nemesis scanner and related `mvt` package components.
- IOC indicator and repository foundations.
- DNS filtering and VPN service scaffold (`GuardVpnService`).
- Threat scanner and dashboard activity flows.
- Unit tests for security evaluators and scanner components.
- Android CI workflow.

### 4.2 Not yet verified or complete

The following must not be represented as production complete:

- Full physical-device compatibility testing across a broad matrix.
- Universal malware detection guarantees.
- Pegasus detection guarantees.
- Forensic acquisition guarantees.
- Production signed IOC backend and production key infrastructure.
- Server-validated billing entitlements.
- Final Play Store legal/policy approvals.
- Final privacy policy and Data Safety declarations.
- Final screenshots captured from a validated release build.

---

## 5. Required technology baseline

### 5.1 Current repository build configuration (verified)

```kotlin
namespace = "com.coldboar.coreguard"
applicationId = "com.coldboar.coreguard"
minSdk = 24
compileSdk = 34
targetSdk = 34
versionCode = 1
versionName = "1.0.0"
jvmTarget = "17"
```

### 5.2 Target product baseline (if migrating to Elite package later)

```kotlin
namespace = "com.app.coldboarcoreguard"
applicationId = "com.app.coldboarcoreguard"
minSdk = 26
compileSdk = 35
targetSdk = 35
versionCode = 1
versionName = "1.0.0"
jvmTarget = "17"
```

If migration to the target product baseline is performed, it must be completed as a controlled migration plan with explicit test and policy validation gates.

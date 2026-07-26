# CoreGuard Release Readiness Guide

> Version 1.0 — July 2026  
> **Honest statement**: This document describes the steps needed to ship.
> It does not claim guaranteed Play Store approval, completed billing, or
> production-grade security guarantees where those are not yet implemented.

---

## Table of Contents

1. [Known Limitations & What Is Simulated](#1-known-limitations--what-is-simulated)
2. [App Signing](#2-app-signing)
3. [AAB Generation](#3-aab-generation)
4. [Play Console Setup](#4-play-console-setup)
5. [Data Safety & Privacy](#5-data-safety--privacy)
6. [Billing Setup](#6-billing-setup)
7. [Testing Tracks](#7-testing-tracks)
8. [Pre-Launch Checklist](#8-pre-launch-checklist)

---

## 1. Known Limitations & What Is Simulated

| Feature | Status | Notes |
|---------|--------|-------|
| **CPU usage** | 🟡 BASIC | Aggregate CPU usage is sampled from `/proc/stat` on-device. This is a coarse overall reading, not a per-process or security-specific signal. |
| **Billing / premium** | 🟢 PLAY BILLING | `PlayBillingProvider` is the production path (Settings + Paywall). `DemoBillingProvider` is tests-only. Create Play Console product `coreguard_premium_monthly` and test with license testers before production. |
| **Purchase verification** | 🟡 CLIENT ACK ONLY | Purchases are acknowledged on-device. Server-side Google Play Developer API verification is recommended before high-trust entitlement gating. |
| **Signature pinning** | 🟡 PARTIALLY IMPLEMENTED | `SignatureCheckEvaluator` exists but `expectedSha256` is empty in demo — always WARN. Must be populated with the real signing certificate hash before release. |
| **Root / emulator detection** | 🟡 HEURISTIC | Heuristic checks only. Advanced root frameworks may not be detected. |
| **Play Store approval** | ⬛ NOT GUARANTEED | Submitting this app does not guarantee approval. Google reviews apps for policy compliance independently. |

---

## 2. App Signing

### Why It Matters
Google Play requires a signed AAB/APK. If you lose the keystore you lose the ability to update the app.

### Steps

1. **Generate a keystore** (do this once; store it securely outside the repo):
   ```bash
   keytool -genkeypair \
     -v \
     -keystore coreguard-release.jks \
     -keyalg RSA \
     -keysize 2048 \
     -validity 10000 \
     -alias coreguard \
     -storepass <STORE_PASSWORD> \
     -keypass <SIGNING_KEY_PASSWORD>
   ```

2. **Store credentials safely** — use environment variables or a secrets manager.
   Never commit the keystore or passwords to the repository.

3. **Configure signing in `gradle/android-app.gradle`**:
   ```kotlin
   android {
       signingConfigs {
           create("release") {
               // Configured in gradle/android-app.gradle via SIGNING_* env or keystore.properties
               storePassword = System.getenv("SIGNING_STORE_PASSWORD")
               keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: "coreguard"
               keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
           }
       }
       buildTypes {
           release {
               signingConfig = signingConfigs.getByName("release")
               // ... existing config
           }
       }
   }
   ```

4. **Enroll in Play App Signing** (recommended):
   Upload your upload key to Google and let Play manage the final signing key.
   This protects against keystore loss.

5. **Update `SignatureCheckEvaluator`**:
   After signing, set `EXPECTED_CERT_SHA256` (env) or `expectedCertSha256` in
   `keystore.properties`. Gradle can also derive it from the release keystore via
   `keytool` so Guardian Score no longer WARN-only for an empty pin.

---

## 3. AAB Generation

```bash
# Set environment variables first (see Signing section above)
export SIGNING_STORE_FILE=/path/to/coreguard-release.jks
export SIGNING_STORE_PASSWORD=<store_pass>
export SIGNING_KEY_ALIAS=coreguard
export SIGNING_KEY_PASSWORD=<key_pass>

./gradlew bundleRelease
```

Output: `app/build/outputs/bundle/release/app-release.aab`

To build a debug APK for quick testing:
```bash
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

---

## 4. Play Console Setup

1. Go to [play.google.com/console](https://play.google.com/console) and create an app.
2. Set **package name** to `com.coldboar.coreguard`.
3. Complete the **Store listing** (screenshots, description, icon — minimum 512×512 px icon required).
4. Upload the AAB to the **Internal Testing** track first.
5. Complete the **App content** questionnaire (ads, target audience, etc.).
6. Set up **App signing** under Release → Setup → App signing.

### Declarations Required

- **Target audience**: Select appropriate age rating.
- **Sensitive permissions**: `READ_PHONE_STATE` (used for device ID in older APIs) — justify in questionnaire.
- **Deceptive behavior**: Do not claim security guarantees the app cannot provide.

---

## 5. Data Safety & Privacy

CoreGuard v1 does **not** create user accounts and does **not** upload scan results to a CoreGuard backend.
Scans and Quilla Q&A run on-device. The app **does** use the network when you enable:

- optional Premium threat signature refresh (HTTPS)
- optional Quilla Research STIX sync (HTTPS)
- Google Play Billing
- Privacy Shield DNS forwarding for allowed queries

In the Data Safety form on Play Console, follow `docs/PLAY_CONSOLE_CHECKLIST.md` (not an absolute “no network” answer).
Disclose optional Scam Guard **Notification Listener** access: on-device notification text heuristics only when the user grants Notification access; not shared off-device.

**Privacy Policy**: Host a policy that matches the above. In-app screen + `docs/privacy-policy.html` are the source of truth.

---

## 6. Billing Setup

**Current state**: Production path is `PlayBillingProvider` (wired from `CoreGuardApplication` / `MainActivity` / `PaywallActivity`).
`DemoBillingProvider` is for JVM unit tests and Compose previews only — never ship it as production billing.

Authoritative subscription product ID: `BillingProvider.PREMIUM_PRODUCT_ID` = `coreguard_premium_monthly`.

### Still required before charging real users

1. Create the subscription in Play Console with product ID `coreguard_premium_monthly`.
2. Add license testers and verify purchase → entitlement → export/refresh/timeline on a device.
3. Prefer server-side Google Play Developer API verification before high-trust entitlement gating
   (client-side ack alone is what ships today).

---

## 7. Testing Tracks

| Track | Purpose | Who Gets It |
|-------|---------|------------|
| **Internal Testing** | Fast iteration; up to 100 testers | Team members |
| **Closed Testing (Alpha)** | Broader validation; specific email groups | Selected beta testers |
| **Open Testing (Beta)** | Public opt-in beta | Anyone who opts in |
| **Production** | Full public release | All users |

### Recommended Order
1. Fix all FAIL checks in Security Dashboard for the test device.
2. Deploy to Internal Testing.
3. Run through all UI flows manually.
4. Run `./gradlew test` — all unit tests must pass.
5. Promote to Closed Testing after no regressions found.
6. Complete Play Console review questionnaires.
7. Promote to Production only after billing is fully integrated and tested.

---

## 8. Pre-Launch Checklist

- [ ] All unit tests pass: `./gradlew -Pcoreguard.androidBuild=true :app:testDebugUnitTest`
- [ ] Debug APK builds cleanly: `./gradlew -Pcoreguard.androidBuild=true :app:assembleDebug`
- [x] Release AAB builds cleanly: `./gradlew -Pcoreguard.androidBuild=true :app:bundleRelease` (v1.0.14 / versionCode 15)
- [x] Release manifest blocks app backup/data extraction and cleartext HTTP by default
- [x] Production billing path is `PlayBillingProvider` (Demo is tests/previews only)
- [x] Authoritative SKU is `BillingProvider.PREMIUM_PRODUCT_ID` = `coreguard_premium_monthly`
- [ ] Play Console subscription created + license-tester purchase verified on device
- [ ] Server-side purchase token verification implemented (recommended)
- [ ] `EXPECTED_CERT_SHA256` / signature check set to real cert hash
- [ ] ProGuard/R8 release build tested (check no critical classes stripped)
- [x] App icon (512×512 px) present under `store/`
- [ ] Play Console store listing completed (real device screenshots)
- [ ] Privacy policy URL added to Play Console
- [ ] Data Safety form completed (see `PLAY_CONSOLE_CHECKLIST.md`)
- [ ] Target audience / content rating questionnaire completed
- [ ] App tested on at least one physical device (not only emulator) — see `MANUAL_RELEASE_TEST.md`
- [ ] Security Dashboard shows expected PASS/WARN states on a non-rooted device

Claims matrix: [`SECURITY_CLAIMS.md`](SECURITY_CLAIMS.md). Quality-pass notes: [`NINE_TEN_PASS_SUMMARY.md`](NINE_TEN_PASS_SUMMARY.md).

---

*Maintaining honesty in this document is essential. Update it whenever the implementation status of any item changes.*

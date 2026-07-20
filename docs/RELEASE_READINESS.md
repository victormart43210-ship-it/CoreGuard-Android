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
| **CPU usage** | 🔴 SIMULATED | No real CPU reading. Labeled "simulated" in UI and code. Must be replaced with a real `ActivityManager` or `/proc/stat` implementation before claiming real metrics. |
| **Billing / premium** | 🟡 INTEGRATED | Debug = demo. Release = Play Billing + **billing-server** verification gate. |
| **Purchase verification** | 🟢 IMPLEMENTED (deploy required) | `:billing-server` verifies tokens via Play Developer API (or mock mode). App grants premium only after `active:true`. You must deploy the server and set `COREGUARD_VERIFY_URL`. |
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
     -keypass <KEY_PASSWORD>
   ```

2. **Store credentials safely** — use environment variables or a secrets manager.
   Never commit the keystore or passwords to the repository.

3. **Configure signing in `app/build.gradle.kts`**:
   ```kotlin
   android {
       signingConfigs {
           create("release") {
               storeFile = file(System.getenv("KEYSTORE_PATH") ?: "coreguard-release.jks")
               storePassword = System.getenv("KEYSTORE_PASSWORD")
               keyAlias = System.getenv("KEY_ALIAS") ?: "coreguard"
               keyPassword = System.getenv("KEY_PASSWORD")
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
   After signing, record the SHA-256 of your signing certificate and set
   `expectedSha256` in `SecurityDashboardActivity` to enable signature pinning.

---

## 3. AAB Generation

```bash
# Set environment variables first (see Signing section above)
export KEYSTORE_PATH=/path/to/coreguard-release.jks
export KEYSTORE_PASSWORD=<store_pass>
export KEY_ALIAS=coreguard
export KEY_PASSWORD=<key_pass>

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
- **Permissions**: v1 declares **no** dangerous/runtime permissions. If you add any later, justify each in the Play Console questionnaire.
- **Deceptive behavior**: Do not claim security guarantees the app cannot provide. Do not present demo unlock as a real purchase.

---

## 5. Data Safety & Privacy

CoreGuard v1 collects **no personal data** and makes **no network requests**.

In the Data Safety form on Play Console:

| Question | Answer |
|----------|--------|
| Does your app collect or share user data? | No |
| Does the app use encryption in transit? | N/A (no network) |
| Does the app provide a way to delete user data? | N/A |

> If a backend is added in a later version, revisit and update this section and
> the Data Safety form before publishing that version.

**Privacy Policy**: Even with no data collection, Google Play may require a
privacy policy URL. Host a simple policy stating no data is collected.

---

## 6. Billing Setup

**Current state**
| Build type | Provider | Honesty |
|------------|----------|---------|
| **debug** (`USE_DEMO_BILLING=true`) | `DemoBillingProvider` | Simulated unlock — **not** a purchase |
| **release** (`USE_DEMO_BILLING=false`) | `PlayBillingProvider` + `PurchaseVerifier` | Play sheet → acknowledge → **billing-server** verify → premium |

See **[PLAY_CONSOLE_BILLING.md](PLAY_CONSOLE_BILLING.md)** for Play Console product + Internal Testing steps, and **[../billing-server/README.md](../billing-server/README.md)** for the verifier service.

**Account steps still required (cannot be done without your Play JSON / hosting)**
- Drop Play-linked `~/coreguard-secrets/play-service-account.json` and re-run `./scripts/play_pipeline.sh`
- Deploy billing-server to a **public HTTPS** URL and set `COREGUARD_VERIFY_URL`
- Finish base-plan pricing in Play Console if the API leaves a draft product
- Host [PRIVACY_POLICY.md](PRIVACY_POLICY.md) at a public URL for Play Console
- Install from the Internal Testing **opt-in link** (not a sideload QR)

### Wire-up already in the repo

1. `billing-ktx` + `PlayBillingProvider` (client)
2. `HttpPurchaseVerifier` → `POST /v1/subscriptions/verify`
3. `:billing-server` (Ktor) with Google Play Developer API or `COREGUARD_VERIFY_MODE=mock`
4. Release signing via env (`KEYSTORE_PATH`, etc.) when set — secrets never committed
5. `scripts/play_pipeline.sh` — product ensure → server → AAB → Internal Testing upload

---

## 7. Testing Tracks & pipeline

```bash
export COREGUARD_VERIFY_URL=https://your-public-billing-server.example
export COREGUARD_VERIFY_MODE=google
./scripts/play_pipeline.sh
```

| Track | Purpose | Who Gets It |
|-------|---------|------------|
| **Internal Testing** | Fast iteration; up to 100 testers | Team members |
| **Closed Testing (Alpha)** | Broader validation; specific email groups | Selected beta testers |
| **Open Testing (Beta)** | Public opt-in beta | Anyone who opts in |
| **Production** | Full public release | All users |

### Recommended Order
1. Fix all FAIL checks in Security Dashboard for the test device.
2. Run `./scripts/play_pipeline.sh` with credentials + public VERIFY_URL.
3. Install from Internal Testing opt-in link; run through UI flows.
4. Run `./gradlew test` — all unit tests must pass.
5. Promote to Closed Testing after no regressions found.
6. Complete Play Console review questionnaires.
7. Promote to Production only after billing verification is live and tested.

---

## 8. Pre-Launch Checklist

- [ ] All unit tests pass: `./gradlew test`
- [ ] Debug APK builds cleanly: `./gradlew assembleDebug`
- [ ] Release AAB builds cleanly: `./gradlew bundleRelease`
- [ ] CPU metric is no longer labeled "simulated" or is removed from release UI
- [ ] Play Console subscription `coreguard_premium_monthly` created and tested on Internal Testing (see `docs/PLAY_CONSOLE_BILLING.md`)
- [ ] `billing-server` deployed with Play service account; release built with `COREGUARD_VERIFY_URL`
- [ ] `expectedSha256` in `SignatureCheckEvaluator` set to real cert hash
- [ ] ProGuard/R8 release build tested (check no critical classes stripped)
- [ ] App icon (512×512 px) created and set
- [ ] Play Console store listing completed
- [ ] Privacy policy URL hosted (`docs/PRIVACY_POLICY.md`) and added in Play Console
- [ ] Data Safety form completed (update if you collect purchase/account data)
- [ ] Target audience / content rating questionnaire completed
- [ ] App tested on at least one physical device via a Play testing track (not only sideload)
- [ ] Security Dashboard shows expected PASS/WARN states on a non-rooted device

---

*Maintaining honesty in this document is essential. Update it whenever the implementation status of any item changes.*

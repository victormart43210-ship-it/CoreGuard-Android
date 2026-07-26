# CoreGuard Play Store Launch Guide

> Companion to [`RELEASE_READINESS.md`](RELEASE_READINESS.md) (detailed steps and
> current implementation status) and [`LAUNCH_GATE_FAST.md`](LAUNCH_GATE_FAST.md)
> (fast go/no-go checklist for test tracks).

## 1. Prerequisites

- Google Play Developer account (one-time $25 fee)
- A signed Android App Bundle (AAB)
- App icon (512x512 PNG)
- Feature graphic (1024x500 PNG)
- At least 2-5 screenshots
- Privacy Policy URL (required for data apps)

## 2. Build & Signing

- Generate a release keystore using `keytool`
- Configure `signingConfig` in Android Gradle
- Build release bundle: `./gradlew bundleRelease`
- Output file: `app-release.aab`
- Do NOT lose your keystore file (cannot recover)

## 3. Play Console Setup

- Create new app in Google Play Console
- Fill app details (name, description, category)
- Select app type (App or Game)
- Set content rating questionnaire
- Declare data safety form

## 4. Store Listing Assets

- Short description (80 chars max)
- Full description (marketing + keywords)
- App icon (512x512)
- Screenshots (phone + tablet if possible)
- Optional promo video (YouTube link)

## 5. Policies & Compliance

- Must include privacy policy link
- Declare background services if used
- Declare network access usage
- If VPN or device monitoring is used, disclose clearly
- No misleading claims about spying or hacking users

## 6. Testing & Release

- Upload AAB to Internal Testing track first
- Test on real devices
- Fix crashes and ANRs
- Move to Closed Testing
- Then Production rollout (gradual release recommended)

## 7. Backend Readiness

CoreGuard does **not** ship a login/API backend in this repository.
Do not treat “API deployed” or “Login works” as launch gates for this app.

Still verify:

- Optional HTTPS IOC / Quilla Research fetches behave correctly on device
- Play Billing product + license testers
- Privacy Policy URL hosted and linked

## 8. Final Checklist

- No crashes on startup (device/emulator evidence)
- Billing tested with license testers (`coreguard_premium_monthly`)
- All permissions justified in Play Console questionnaires
- Claims match [`SECURITY_CLAIMS.md`](SECURITY_CLAIMS.md)
- Manual smoke path in [`MANUAL_RELEASE_TEST.md`](MANUAL_RELEASE_TEST.md)
- App complies with Google Play policies (human review)

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

- Ensure API is deployed (Render/Vercel/etc.)
- Use HTTPS only endpoints
- Enable authentication (Firebase or JWT)
- Enable rate limiting and logging

## 8. Final Checklist

- No crashes on startup
- Login works end-to-end
- Billing tested (if applicable)
- All permissions justified
- App complies with Google Play policies

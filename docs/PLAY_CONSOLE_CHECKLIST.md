# Play Console ship checklist

Use this after merging the Play Store readiness branch. Code + assets in-repo are the baseline; Console steps still need a human with Play Developer access.

## In this repo (code / assets)

- [x] `targetSdk` / `compileSdk` 35, `versionCode` 15 / `versionName` 1.0.14 (source: `gradle/android-app.gradle`)
- [x] `com.android.vending.BILLING` declared; production path is `PlayBillingProvider`
- [x] Unused `READ_PHONE_STATE` removed
- [x] Honest store listing + paywall copy (no magical spyware guarantees)
- [x] Privacy Shield discloses DNS upstream forwarding
- [x] `POST_NOTIFICATIONS` requested before Shield on Android 13+
- [x] Optional Scam Guard `NotificationListenerService` disclosed in privacy policy + Data Safety answers below
- [x] Launcher icons (mipmap) + `store/` listing graphics
- [x] Hosted privacy policy HTML (`docs/privacy-policy.html`) + Pages workflow
- [x] In-app Privacy Policy screen + `privacy_policy_url` string

## Play Console (you must do)

1. **Create app** with package `com.coldboar.coreguard`
2. **Store listing** — paste `store_short_description` / `store_full_description`; upload `store/play_icon_512.png`, `store/feature_graphic_1024x500.png`, and **real device screenshots** (replace placeholder)
3. **Privacy policy URL** — enable GitHub Pages (workflow `.github/workflows/pages.yml`) or host elsewhere; set URL to match `privacy_policy_url`
4. **App content**
   - VPN app declaration (Privacy Shield uses `VpnService` for local DNS filtering)
   - Foreground service / special-use disclosure
   - `QUERY_ALL_PACKAGES` declaration (core spyware package checks)
   - **Notification Listener** declaration for optional Scam Guard (user must grant Notification access)
   - Data Safety form (see below)
5. **Monetize** — create subscription product ID `coreguard_premium_monthly`; add license testers
6. **Signing** — upload key / Play App Signing; set `EXPECTED_CERT_SHA256` for release builds
7. **Release** — upload AAB (`v1.0.14` / `versionCode` 15) to Internal testing → Closed → Production (staged rollout)

## Data Safety form (recommended answers)

| Question | Answer |
|----------|--------|
| Collects personal data? | **No** account collection; scans stay on-device. Optional Scam Guard notification text is processed **on device only** when the user enables Notification access — not shared by CoreGuard |
| Location / contacts / photos? | **No** |
| Financial info? | **No** (Play handles payments) |
| App activity / installed apps? | Processed **on device** to match spyware package indicators; **not shared** by CoreGuard |
| Messages / other communication? | Optional Scam Guard: notification **text** may be read **on device** for scam/smishing URL heuristics after the user grants Notification access; **not collected or shared** off-device |
| Device IDs for advertising? | **No** |
| Data encrypted in transit? | Optional HTTPS IOC fetches: **yes** |
| Users can request deletion? | N/A — CoreGuard does not collect account data; uninstall clears local app storage; revoking Notification access stops Scam Guard live interception |

## Build commands

```bash
./gradlew -Pcoreguard.androidBuild=true :app:testDebugUnitTest
./gradlew -Pcoreguard.androidBuild=true :app:lintDebug
./gradlew -Pcoreguard.androidBuild=true :app:assembleRelease
./gradlew -Pcoreguard.androidBuild=true :app:bundleRelease   # needs release signing env
```

## Still not guaranteed by code alone

- Play policy approval
- Real screenshots / feature graphic quality review
- Target API 36 deadline (Aug 31, 2026 for updates — plan a follow-up bump)
- Server-side purchase verification (recommended before high-trust gating)

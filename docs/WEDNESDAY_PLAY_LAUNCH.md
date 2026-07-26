# Wednesday Play Launch — get CoreGuard on a device and into Play

This is the shortest path from “code in repo” to **Internal Testing** on Google Play.

## What’s already green in this environment

| Artifact | Status |
|----------|--------|
| Real debug APK (`-Pcoreguard.androidBuild=true`) | Built — `app/build/outputs/apk/debug/app-debug.apk` (~22 MB) |
| Signed release AAB | Built when `keystore.properties` / signing env is present |
| AVD `CoreGuard_API35` (API 35 / Pixel 6 / x86_64) | Created via `scripts/setup-android-sdk.sh` |
| Store assets | `store/play_icon_512.png`, feature graphic, listing copy in `strings.xml` |

> Cloud VMs often **lack `/dev/kvm`**. Emulator still boots with software graphics, but it is slow. Prefer Android Studio + hardware accel on your laptop for UI testing.

---

## Day plan (Mon → Wed)

### Monday — merge, local run, Play Console shell

1. **Merge** [PR #70](https://github.com/victormart43210-ship-it/CoreGuard-Android/pull/70) (or rebase onto `main` and ship).
2. On your Mac/Linux machine with KVM or Android Studio:
   ```bash
   git pull
   ./scripts/setup-android-sdk.sh          # once
   ./gradlew -Pcoreguard.androidBuild=true :app:assembleDebug
   ./scripts/run-emulator.sh               # installs + launches debug app
   ```
   Debug package id: `com.coldboar.coreguard.debug`
3. Create the Play Console app (if missing):
   - App name: **CoreGuard**
   - Package: **`com.coldboar.coreguard`** (release — no `.debug` suffix)
   - Free app, category Tools / Productivity
4. Host privacy policy (GitHub Pages already documented):
   `https://victormart43210-ship-it.github.io/CoreGuard-Android/privacy-policy.html`

### Tuesday — signing, AAB, Internal Testing

1. **Upload keystore (once)** — back it up offline immediately:
   ```bash
   ./scripts/prepare-upload-keystore.sh ~/coreguard-secrets
   # creates gitignored keystore.properties + ~/coreguard-secrets/passwords.env
   ```
2. Build signed bundle:
   ```bash
   source ~/coreguard-secrets/passwords.env
   export EXPECTED_CERT_SHA256='<SHA256 printed by prepare-upload-keystore.sh>'
   ./gradlew -Pcoreguard.androidBuild=true :app:bundleRelease
   ```
   Output: `app/build/outputs/bundle/release/app-release.aab`
3. Play Console → **Internal testing** → create release → upload AAB.
4. Enroll **Play App Signing** (recommended).
5. Create subscription product: `coreguard_premium_monthly`.
6. Add yourself as a **license tester**; install via Internal Testing link on a real phone.

### Wednesday — smoke, Data Safety, promote

1. Run [`MANUAL_RELEASE_TEST.md`](MANUAL_RELEASE_TEST.md) on a physical device.
2. Complete **Data Safety**, content rating, VPN / sensitive permissions declarations (Shield uses VPN).
3. Claims must match [`SECURITY_CLAIMS.md`](SECURITY_CLAIMS.md) — no “guaranteed spyware removal”.
4. Promote Internal → Closed (optional) → Production (staged 20% first).

---

## One-command local loop

```bash
export ANDROID_HOME="$HOME/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"

./scripts/setup-android-sdk.sh   # skip if already done
./scripts/run-emulator.sh
```

Headless / CI-style boot (no window):

```bash
HEADLESS=1 ./scripts/run-emulator.sh
```

---

## Release signing (how Gradle finds credentials)

1. Env vars (CI): `SIGNING_STORE_FILE`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`
2. Or local gitignored `keystore.properties` (from `./scripts/prepare-upload-keystore.sh`)

Never commit `*.jks`, `*.keystore`, or `keystore.properties`.

Set `EXPECTED_CERT_SHA256` to the upload cert’s SHA-256 so the in-app signature check stops warning.

---

## Hard blockers before Production

- [ ] Upload keystore backed up offline (and Play App Signing enrolled)
- [ ] Privacy policy URL live
- [ ] Data Safety form accurate (VPN / on-device processing)
- [ ] Billing product + license tester purchase path
- [ ] No crash on cold start / scan / shield arm on a real device
- [ ] Store listing screenshots replaced with real captures (`store/` placeholders are starter only)

See also: [`PLAYSTORE_LAUNCH_GUIDE.md`](PLAYSTORE_LAUNCH_GUIDE.md), [`PLAY_CONSOLE_CHECKLIST.md`](PLAY_CONSOLE_CHECKLIST.md), [`RELEASE_READINESS.md`](RELEASE_READINESS.md).

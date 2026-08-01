# Certificate verification (Play App Signing)

CoreGuard validates the **installed** app signing certificate at runtime via
`SignatureCheckEvaluator` and `BuildConfig.EXPECTED_CERT_SHA256`.

## Three certificates (do not confuse them)

| Certificate | Used for | Set `EXPECTED_CERT_SHA256` to? |
|-------------|----------|--------------------------------|
| **Upload key** | Signs the AAB you upload to Play Console | Usually **no** (unless you opted out of Play App Signing) |
| **Play app-signing key** | Google re-signs the APK/AAB users install | **Yes** for production Play builds |
| **Debug key** | Local `assembleDebug` / `.debug` applicationId | Leave empty or unset — evaluator reports **WARN**, not PASS |

## How to obtain the Play app-signing SHA-256

1. Play Console → Your app → **Setup → App signing**
2. Copy **App signing key certificate** SHA-256
3. Store as GitHub secret `EXPECTED_CERT_SHA256` and/or `keystore.properties` key `expectedCertSha256`

## Gradle behavior

- Signing env vars: `SIGNING_STORE_FILE`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`
- Partial signing config (**1–3 of 4**) fails the Gradle configuration phase
- `COREGUARD_REQUIRE_RELEASE_SIGNING=true` fails if release signing is absent (CI release job)
- Passwords are never printed; only alias / success messages are logged
- If `EXPECTED_CERT_SHA256` is empty, runtime check is **WARN** (honest), never a false PASS

## Runtime honesty

A missing expected fingerprint must not claim the device is cryptographically verified.
Users see a warning to configure the Play app-signing pin for production.

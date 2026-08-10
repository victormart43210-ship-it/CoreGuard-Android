# Signed AAB CI setup

Release workflow: `/home/runner/work/CoreGuard-Android/CoreGuard-Android/.github/workflows/release.yml`

## Required GitHub secrets

- `RELEASE_KEYSTORE_BASE64`
- `SIGNING_KEY_ALIAS`
- `SIGNING_KEY_PASSWORD`
- `SIGNING_STORE_PASSWORD`
- `EXPECTED_CERT_SHA256` (recommended; Play app-signing cert)
- `THREAT_INTEL_HMAC_KEY` (for threat-intel artifact signatures)

## Local setup

1. Generate keystore and gitignored `keystore.properties`:
   - `./scripts/prepare-upload-keystore.sh`
2. Load env vars from generated `passwords.env`
3. Build signed bundle:
   - `COREGUARD_REQUIRE_RELEASE_SIGNING=true ./gradlew :app:bundleRelease`

## CI behavior

- Workflow verifies required signing secrets before release build.
- Keystore is decoded into `${{ runner.temp }}` only.
- Release artifacts are uploaded as `release-aab-<tag>` with checksums.
- Threat-intel workflow signs bundle artifacts when `THREAT_INTEL_HMAC_KEY` is present.

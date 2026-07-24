# CoreGuard-Android

Native Kotlin Android security and device-monitoring **prototype**
(`com.coldboar.coreguard`).

This repository is a production-honest scaffold for Play Store preparation.
It is **not** a claim of Play approval, production billing, or guaranteed
security against advanced attacks.

## Implementation Handoff

- SRD: `docs/CoreGuard_Elite_Copilot_SRD.md`
- Artifact generator: `scripts/generate_coreguard_handoff.py`
- Generate handoff files (`.md`, `.docx`, `.zip`) to `build/handoff`:
  - `python3 scripts/generate_coreguard_handoff.py`
- Optional custom output and logo:
  - `python3 scripts/generate_coreguard_handoff.py --out-dir /mnt/data --logo /path/to/logo.png`

## What works today

- Security Status dashboard with PASS / WARN / FAIL for:
  - debugger attached
  - emulator indicators
  - root indicators (heuristic)
  - app signature integrity (WARN until expected hash is configured)
  - build type (debug / release)
- Lifecycle-safe RAM polling with null-safe memory helpers
- Explicitly **simulated** CPU label (not measured)
- Demo entitlement path clearly separated from future Play Billing
- Play Billing path guarded by server verification before premium unlock
- Nemesis Scanner and on-device privacy shield flows from `main`
- Unit tests for memory math, entitlements, and security evaluators

## What is simulated / incomplete

| Area | Status |
|------|--------|
| CPU usage | Simulated — labeled in UI |
| Premium unlock (debug) | `DemoBillingProvider` — not a purchase |
| Premium unlock (release) | Play Billing + `billing-server` token verification |
| Signature pinning | Evaluator present; expected hash not configured |
| Play Store approval | Not claimed |

## billing-server

```bash
export COREGUARD_VERIFY_MODE=mock
./gradlew :billing-server:run
```

Release app builds need `COREGUARD_VERIFY_URL` pointing at a deployed verifier.
See [docs/PLAY_CONSOLE_BILLING.md](docs/PLAY_CONSOLE_BILLING.md).

See [docs/RELEASE_READINESS.md](docs/RELEASE_READINESS.md),
[docs/SECURITY_BASELINE.md](docs/SECURITY_BASELINE.md), and
[docs/THREAT_MODEL.md](docs/THREAT_MODEL.md).

## Build

Requirements: JDK 17+, Android SDK (compile/target SDK 34).

```bash
./gradlew test
./gradlew assembleDebug
./gradlew lint   # optional when SDK is available
```

Release AAB (requires your own keystore — never commit it):

```bash
./gradlew bundleRelease
```

CI runs `./gradlew test`, `./gradlew :app:lintDebug`, and
`./gradlew assembleDebug` (see [.github/workflows/android.yml](.github/workflows/android.yml)).

## Contribution Guidelines

Before each commit, run `./gradlew test` and ensure the app still builds with
`./gradlew assembleDebug`. Run `./gradlew lint` when the Android SDK is
available, and update `README.md` when adding or changing user-visible features.

## Package

- applicationId / namespace: `com.coldboar.coreguard`
- Min SDK 24, target / compile SDK 34

## Secrets

Never commit keystores, API keys, or `local.properties`.
`.gitignore` excludes `*.jks`, `*.keystore`, and related secrets.

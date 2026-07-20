# CoreGuard-Android

Native Kotlin Android security and device-monitoring **prototype**
(`com.coldboar.coreguard`).

This repository is a production-honest scaffold for Play Store preparation.
It is **not** a claim of Play approval, production billing, or guaranteed
security against advanced attacks.

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
- Unit tests for memory math, entitlements, and security evaluators

## What is simulated / incomplete

| Area | Status |
|------|--------|
| CPU usage | Simulated — labeled in UI |
| Premium unlock | `DemoBillingProvider` only — not purchase verification |
| Signature pinning | Evaluator present; expected hash not configured |
| Google Play Billing | Interface + `PlayBillingProvider` stub only |
| Play Store approval | Not claimed |

See [docs/RELEASE_READINESS.md](docs/RELEASE_READINESS.md) and
[docs/THREAT_MODEL.md](docs/THREAT_MODEL.md).

## Build

Requirements: JDK 17+, Android SDK (compile/target SDK 34).

```bash
./gradlew test
./gradlew assembleDebug
./gradlew lint   # optional
```

Release AAB (requires your own keystore — never commit it):

```bash
./gradlew bundleRelease
```

CI runs `./gradlew test` and `./gradlew assembleDebug`
(see [.github/workflows/android.yml](.github/workflows/android.yml)).

## Package

- applicationId / namespace: `com.coldboar.coreguard`
- Min SDK 24, target / compile SDK 34

## Secrets

Never commit keystores, API keys, or `local.properties`.
`.gitignore` excludes `*.jks`, `*.keystore`, and related secrets.

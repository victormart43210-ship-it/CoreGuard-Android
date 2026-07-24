# CoreGuard-Android

Native Kotlin Android security and device-monitoring **prototype**
(`com.coldboar.coreguard`).

This repository is a production-honest scaffold for Play Store preparation.
It is **not** a claim of Play approval, guaranteed security, or premium unlock
without Google Play plus server verification.

## Official Distribution

| Attribute | Value |
|-----------|-------|
| Package name | `com.coldboar.coreguard` |
| Official releases | [GitHub Releases](https://github.com/victormart43210-ship-it/CoreGuard-Android/releases) |
| Security policy | [SECURITY.md](SECURITY.md) |

Release AABs and their SHA-256 checksums are published on the Releases page.
Each release also carries a GitHub artifact attestation that links the binary to
its source commit and workflow:

```bash
gh attestation verify <path-to-aab> --repo victormart43210-ship-it/CoreGuard-Android
```

Only install artifacts that match the published checksum and attestation.

## Features

| Area | Status |
|------|--------|
| Device RAM monitoring | Real `ActivityManager` readings |
| CPU usage | **Simulated** and labeled as such |
| Security dashboard | Local heuristic checks (debugger / root / emulator / signature / build type / spyware) |
| Network Defense Lab | Educational simulation in app + companion CLI |
| Premium unlock (debug) | `DemoBillingProvider` — not a purchase |
| Premium unlock (release) | Play Billing + `billing-server` verification gate |

## Implementation Handoff

- SRD: `docs/CoreGuard_Elite_Copilot_SRD.md`
- Artifact generator: `scripts/generate_coreguard_handoff.py`
- Generate handoff files (`.md`, `.docx`, `.zip`) to `build/handoff`:
  - `python3 scripts/generate_coreguard_handoff.py`
- Optional custom output and logo:
  - `python3 scripts/generate_coreguard_handoff.py --out-dir /mnt/data --logo /path/to/logo.png`

## What is simulated / incomplete

| Area | Status |
|------|--------|
| CPU usage | Simulated — labeled in UI |
| Premium unlock (debug) | `DemoBillingProvider` — not a purchase |
| Signature pinning | Evaluator present; expected hash not configured |
| Play Store approval | Not claimed |

## Network Defense Lab

Interactive topology with live attack/defense/rollback, Prim MST overlay, and a
protanopia-friendly palette. See
[`docs/NETWORK_DEFENSE_LAB.md`](docs/NETWORK_DEFENSE_LAB.md) and
[`cli/README.md`](cli/README.md).

> **Honesty:** The lab is a teaching simulation. It is not live network
> monitoring, intrusion prevention, or a Play-approved security product claim.

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
cd cli && go test -race ./... && go vet ./... && go build -o /tmp/coreguard-cli ./cmd/coreguard
```

Release AAB (requires your own keystore — never commit it):

```bash
./gradlew bundleRelease
```

CI runs the CLI checks, `./gradlew test`, `./gradlew :app:lintDebug`, and
`./gradlew assembleDebug`.

## Contribution Guidelines

Before each commit, run `./gradlew test` and ensure the app still builds with
`./gradlew assembleDebug`. Run `./gradlew lint` when the Android SDK is
available, and update `README.md` when adding or changing user-visible features.

## Package

- applicationId / namespace: `com.coldboar.coreguard`
- Min SDK 24, target / compile SDK 34

## Secrets

Never commit keystores, API keys, or `local.properties`.
`.gitignore` excludes `*.jks`, `*.keystore`, and related secret-like files.

# CoreGuard-Android

Native Kotlin Android security and device-monitoring prototype (`com.coldboar.coreguard`).

## Features

| Area | Status |
|------|--------|
| Device RAM monitoring | Real `ActivityManager` readings |
| CPU usage | **Simulated** (labeled in UI) |
| Security dashboard | Local heuristic checks (debugger / root / emulator / signature) |
| Network Defense Lab | Educational 16-node BFS/DFS + defense simulation |
| Premium unlock | **Demo only** on this branch — not Play Billing verification |
| Companion CLI | Go/Cobra under [`cli/`](cli/) |

## Network Defense Lab

Interactive topology with live attack/defense/rollback, Prim MST overlay, and
protanopia-friendly Okabe–Ito palette with shape markers. See
[`docs/NETWORK_DEFENSE_LAB.md`](docs/NETWORK_DEFENSE_LAB.md) and [`cli/README.md`](cli/README.md).

> **Honesty:** The lab is a teaching simulation. It is not live network monitoring,
> intrusion prevention, or a Play-approved security product claim.

## Expo / tRPC note

The React Native/Expo project referenced in some delivery screenshots is **not**
present in this repository. Missing tRPC context cannot be repaired here.

## Build

```bash
./gradlew test assembleDebug lintDebug
cd cli && go test -race ./... && go vet ./...
```

## Play Store prep

See [`docs/PLAY_STORE_LAUNCH.md`](docs/PLAY_STORE_LAUNCH.md), privacy policy, and
listing assets under [`docs/store-listing/`](docs/store-listing/).

## Implementation Handoff

- SRD: `docs/CoreGuard_Elite_Copilot_SRD.md`
- Artifact generator: `scripts/generate_coreguard_handoff.py`
- Generate handoff files (`.md`, `.docx`, `.zip`) to `build/handoff`:
  - `python3 scripts/generate_coreguard_handoff.py`

## Contribution Guidelines

CoreGuard-Android is a Kotlin-based Android security application focused on detecting risky patterns, improving device security posture, and helping users understand potential threat behavior.

### Code Standards

#### Required Before Each Commit
- Run `./gradlew lint`
- Run `./gradlew test`
- Ensure the app builds with `./gradlew assembleDebug`
- Update `README.md` when adding or changing features

### Kotlin and Android Patterns
- Use Kotlin best practices (null safety, immutability, and clear separation of concerns)
- Prefer interfaces and abstractions for testability
- Keep components modular and focused on a single responsibility

### Security and Performance
- Follow secure coding practices (input validation, least privilege, and safe storage)
- Avoid hardcoded secrets or tokens
- Add defensive error handling for security checks and scanning flows

### Key Guidelines
1. Prioritize secure-by-default implementation.
2. Keep scanning and security logic testable and isolated.
3. Handle failures gracefully and provide actionable user messaging.
4. Validate security-sensitive changes with lint and tests before commit.

## License

Apache License 2.0 — see [`LICENSE`](LICENSE).

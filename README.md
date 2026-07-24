# CoreGuard-Android
<<<<<<< HEAD

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

## License

Apache License 2.0 — see [`LICENSE`](LICENSE).
=======
Native Kotlin Android security and device-monitoring application.

## Implementation Handoff

- SRD: `docs/CoreGuard_Elite_Copilot_SRD.md`
- Artifact generator: `scripts/generate_coreguard_handoff.py`
- Generate handoff files (`.md`, `.docx`, `.zip`) to `build/handoff`:
  - `python3 scripts/generate_coreguard_handoff.py`
- Optional custom output and logo:
  - `python3 scripts/generate_coreguard_handoff.py --out-dir /mnt/data --logo /path/to/logo.png`

## Contribution Guidelines

CoreGuard-Android is a Kotlin-based Android security application focused on detecting risky patterns, improving device security posture, and helping users understand potential threat behavior.

### Code Standards

#### Required Before Each Commit
- Run `./gradlew lint`
- Run `./gradlew test`
- Ensure the app builds with `./gradlew assembleDebug`
- Update `README.md` when adding or changing features
- Keep repository structure and Copilot instructions accurate

### Kotlin and Android Patterns
- Use Kotlin best practices (null safety, immutability, and clear separation of concerns)
- Prefer interfaces and abstractions for testability
- Keep components modular and focused on a single responsibility
- Use lifecycle-aware patterns (such as ViewModel/StateFlow/LiveData where applicable)
- Follow Android architecture guidance for maintainability

### UI and UX
- Use Jetpack Compose best practices (or existing XML patterns where applicable)
- Keep UI components reusable and accessible
- Provide meaningful content descriptions for icons and images used by assistive technologies
- Add proper loading, empty, and error states

### Security and Performance
- Follow secure coding practices (input validation, least privilege, and safe storage)
- Avoid hardcoded secrets or tokens
- Optimize background work, scanning behavior, and battery usage
- Add defensive error handling for security checks and scanning flows

### Development Flow
- Build debug app: `./gradlew assembleDebug`
- Run tests: `./gradlew test`
- Run lint: `./gradlew lint`

### Repository Structure (Android-Oriented)
- `app/`: Android application module
- `app/src/main/`: Production source code, resources, and manifest
- `app/src/test/`: Local unit tests
- `app/src/androidTest/`: Instrumented tests (if present)
- `docs/`: Technical and feature documentation
- `README.md`: Project documentation

### Key Guidelines
1. Prioritize secure-by-default implementation.
2. Keep scanning and security logic testable and isolated.
3. Handle failures gracefully and provide actionable user messaging.
4. Prefer clarity and maintainability over cleverness.
5. Validate security-sensitive changes with lint and tests before commit.
>>>>>>> origin/main

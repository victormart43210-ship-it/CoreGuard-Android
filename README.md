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

## License

Apache License 2.0 — see [`LICENSE`](LICENSE).

# Network Defense Lab

> Prototype educational simulation for `com.coldboar.coreguard`.  
> Not a live network monitor, IPS, or production SOC tool.

## What shipped

| Component | Location |
|-----------|----------|
| Kotlin simulation engine | `app/.../lab/SimulationEngine.kt` |
| Interactive topology view | `app/.../lab/TopologyView.kt` |
| Lab activity | `app/.../lab/NetworkDefenseLabActivity.kt` |
| Accessibility palette | `ColorVisionType.PROTANOPIA` + Okabe–Ito colors + shapes |
| Go companion CLI | `cli/` (`status`, `simulate`, `topology`) |

## Topology

16 nodes `N0`–`N15`: ring edges (weight 1) plus cross chords `Ni–N(i+8)` (weight 2).
Aligned with `cli/internal/sim/graph.go`.

## Operations

- **Attack BFS / DFS** — infect healthy nodes along traversal (blocked by Defended/Isolated)
- **Defend direct / cascade** — mark target (and neighbors for cascade) as Defended
- **Rollback** — restore previous snapshot (events remain append-only)
- **MST** — Prim hardening backbone overlay from `N0`
- **Protanopia** — remapped palette; status also encoded by shape (circle/square/diamond/triangle)

## Limitations (honest)

- In-memory only; no persistence across process death
- Client UI can be bypassed; not an attestation system
- Expo/React Native tRPC app is not in this repo
- No secrets, keystores, or `uv.exe` bundled

## Tests

```bash
./gradlew :app:testDebugUnitTest
cd cli && go test -race ./...
```

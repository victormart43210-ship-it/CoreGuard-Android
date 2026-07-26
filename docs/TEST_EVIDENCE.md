# Test evidence (Cloud Agent session)

Branch: `cursor/swarm-module-redux-counter-6db1`  
Date: 2026-07-26  
Version: **1.0.14** (`versionCode` 15)

> Milestone results are posted as PR comments (GitHub Milestone API returns 403).

## Milestone M18 · Module pattern + Redux Counter separation

| Check | Result | Notes |
|-------|--------|-------|
| `:app:compileDebugKotlin` | **PASS** | EliteModule + EliteThreatCounterStore |
| `:app:testDebugUnitTest` | **PASS** | **318** tests |
| Redux UI contract | **PASS** | SwarmAlertCounter dispatches only via SwarmModule; Elite Home mirrors EliteModule.threatCounter |

### Landed

- `EliteModule` façade for DTS / Scam Guard / Forensic Journal
- `EliteThreatCounterStore` Redux Counter (DTS + scam amber)
- Swarm Counter UI uses `incrementAlertCounter` / `resetAlertCounter` (no Action imports)
- Thorough module/Redux comments on façades, stores, Counter composable

## Milestone M17 · CoreGuard Elite zero-trust feature set

| Check | Result | Notes |
|-------|--------|-------|
| `:app:compileDebugKotlin` | **PASS** | Elite engines + screens |
| `:app:testDebugUnitTest` | **PASS** | **315** tests (was 304) |
| Emulator gate (`CoreGuard_ATD35`) | **PASS** | Instrumented 3 + ADB smoke — MainActivity focused, no fatal |
| Honesty | **PASS** | DTS = on-device correlator + Quilla quantum-inspired; not NPU SLM / cloud LLM. Overlay Matrix audits surfaces (no silent remote overlay-kill). Scam Guard needs user Notification access. Forensic Journal = SHA-256 chain + StrongBox/TEE at rest. |

### Landed

1. **Dynamic Threat Score** — `elite/DynamicThreatEngine` weighted correlator + `QuillaQuantumCorrelate`; BAE started from `CoreGuardApplication`
2. **Overlay Protection Matrix** — screen + route; journals elevated overlay/a11y/sideload findings
3. **Forensic Journal** — append-only SHA-256 chain, encrypted at rest via `HardwareKeyManager`; JSON/CSV export
4. **Scam Guard** — URL/smishing heuristics + optional `NotificationListenerService`; amber pill on Elite Home
5. Quilla codex: `coreguard-elite-platform.json` + ready topics (`elite platform`, `dynamic threat score`, `scam guard`)

### Unit coverage added

- `elite/ScamGuardEngineTest`
- `elite/ForensicJournalTest` (in-memory chain)
- `elite/DynamicThreatEngineTest` (band thresholds + disclaimer)

## Prior milestones

| Milestone | Result |
|-----------|--------|
| M14 Emulator Gate | PASS |
| M15 Swarm + Redux Counter + ATD | PASS — 304 unit + instrumented + smoke |
| M16 CG Elite Home dashboard | PASS — 304 unit + ATD smoke |
| **M17 Elite zero-trust features** | **PASS — 315 unit + ATD instrumented + smoke** |
| **M18 Module + Redux Counter** | **PASS — 318 unit** |

## Release · v1.0.14 Internal Testing

| Step | Status |
|------|--------|
| Merge PR #72 | **DONE** (`bf29ac3` on `main`) |
| Notification Listener disclosure (privacy policy + Play checklist) | **DONE** in-repo |
| `:app:testDebugUnitTest` (release prep) | **PASS** — **318** tests, 0 failures |
| Signed release AAB (`versionCode` 15) | **PASS** — GitHub Release [v1.0.14](https://github.com/victormart43210-ship-it/CoreGuard-Android/releases/tag/v1.0.14) (`app-release.aab`); CI SHA-256 in release `checksums-sha256.txt` |
| GitHub tag `v1.0.14` → Release workflow | **PASS** — [Actions run 30195123549](https://github.com/victormart43210-ship-it/CoreGuard-Android/actions/runs/30195123549) |
| Upload AAB to Play **Internal testing** | **Human** — download AAB from the GitHub Release and upload in Play Console |
| Privacy policy URL on Play listing | **Human** (raw GitHub URL works; Pages workflow may need repo Pages permissions) |
| VPN + `QUERY_ALL_PACKAGES` + Notification Listener declarations | **Human** — see `PLAY_CONSOLE_CHECKLIST.md` |
| `coreguard_premium_monthly` + license tester | **Human** |

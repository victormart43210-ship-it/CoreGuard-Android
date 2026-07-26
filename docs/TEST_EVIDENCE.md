# Test evidence (Cloud Agent session)

Branch: `cursor/swarm-module-redux-counter-6db1`  
Date: 2026-07-26  
Version: **1.0.11** (`versionCode` 12)

> Milestone results are posted as PR comments (GitHub Milestone API returns 403).

## Milestone M15 · Swarm module + Redux Counter + ATD emulator gate

| Check | Result | Notes |
|-------|--------|-------|
| `:app:testDebugUnitTest` | **PASS** | **304** tests |
| Emulator | **PASS** | `CoreGuard_ATD35` still running (google_atd, no KVM) |
| Instrumented (`am instrument`) | **PASS** | Quantum **2/2** · MainActivity launch **1/1** |
| `scripts/smoke-adb.sh` | **PASS** | pid alive, focus on MainActivity, no fatal |
| Gradle wrapper | **8.13** | Pin restores kapt after 9.6.1 breakage on `main` |

### Landed (this branch)

- `SwarmModule` façade + Redux-style `SwarmAlertCounterStore` / UI Counter
- External Security Toolkit rescued from conflicting PR #68
- Emulator harness prefers ATD + `am instrument` path

### Re-run

```bash
HEADLESS=1 ./scripts/quilla-emulator-tests.sh
```

## Prior milestones

| Milestone | Result |
|-----------|--------|
| M1–M13 | PASS (via #70) |
| M14 Emulator Gate | PASS (PR #71) |
| **M15 Swarm + Redux Counter + ATD gate** | **PASS — 304 unit + 3 instrumented + smoke** |

## Play Console next (human)

1. Merge PR #72 (then #71 if still needed) → Internal Testing AAB  
2. Privacy policy URL on Play listing  
3. VPN + `QUERY_ALL_PACKAGES` declarations  
4. `coreguard_premium_monthly` + license tester  

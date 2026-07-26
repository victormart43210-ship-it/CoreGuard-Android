# Test evidence (Cloud Agent session)

Branch: `cursor/premium-ui-atmosphere-6db1`  
Date: 2026-07-26  
Version: **1.0.10** (`versionCode` 11)

> Milestone results are posted as PR comments on [#70](https://github.com/victormart43210-ship-it/CoreGuard-Android/pull/70)
> (GitHub Milestone API returns 403 for the agent token).

## Milestone M14 · Quilla Emulator Gate

| Check | Result | Notes |
|-------|--------|-------|
| `:app:testDebugUnitTest` | **PASS** | **295** tests |
| Emulator boot | **PASS** | `CoreGuard_ATD35` (google_atd x86_64, no KVM / swiftshader) |
| Instrumented (`am instrument`) | **PASS** | QuillaQuantumOnDeviceTest **2/2** · MainActivityLaunchTest **1/1** |
| `scripts/smoke-adb.sh` | **PASS** | Process alive, no fatal |
| Honesty | **PASS** | Host script boots AVD; APK cannot spawn QEMU |

### Landed

- `QuillaEmulatorGate` + knowledge topic `emulator gate`
- `scripts/quilla-emulator-tests.sh` — boot → unit → install x86_64 → `am instrument` → smoke
- androidTest: quantum circuit on-device + debug Application ready
- Application boot defers native/billing under instrumentation (avoids no-KVM ANR)
- `-Pcoreguard.emulatorAbi=x86_64` for lean AVD APKs
- Prefer AVD `CoreGuard_ATD35` when present

### How to re-run

```bash
HEADLESS=1 ./scripts/quilla-emulator-tests.sh
```

## Prior milestones

| Milestone | Result |
|-----------|--------|
| M1–M12 | PASS |
| M13 Quantum Correlate | PASS — 293 tests |
| **M14 Emulator Gate** | **PASS — 295 unit + 3 instrumented + smoke** |

## Play Console next (human)

1. Merge PR #70 → Internal Testing AAB upload  
2. Privacy: `https://raw.githubusercontent.com/victormart43210-ship-it/CoreGuard-Android/main/docs/privacy-policy.html`  
3. VPN + `QUERY_ALL_PACKAGES` declarations  
4. `coreguard_premium_monthly` + license tester  

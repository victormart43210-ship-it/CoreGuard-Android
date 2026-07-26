# Test evidence (Cloud Agent session)

Commit under test: see git tip of `cursor/premium-ui-atmosphere-6db1`.

## Automated

| Check | Result | Notes |
|-------|--------|-------|
| `:app:testDebugUnitTest` | **PASS** | **235** tests, 0 failures / 0 errors |
| `:app:lintDebug` | **PASS** | 0 Error/Fatal; warnings only (deps, unused resources, icons) |
| `:app:assembleDebug` | **PASS** | ~22 MB APK, `com.coldboar.coreguard.debug` |
| `:app:bundleRelease` | **PASS** | ~6.6 MB signed AAB (local upload keystore) |
| Android CI (PR #70) | **PASS** | assemble + unit tests on GitHub runners |

## Emulator (this VM)

| Step | Result | Notes |
|------|--------|-------|
| AVD `CoreGuard_API35` create | PASS | API 35 / google_apis / x86_64 |
| Emulator boot (swiftshader, no KVM) | PASS | Cold boot ~6–10 min |
| `adb install` debug APK | PASS | After PackageManager ready |
| Launch `MainActivity` | PARTIAL | Process starts (Compose loads); **no `/dev/kvm`** → system_server/SystemUI ANRs; UI not usable for interactive smoke here |

**Honest limitation:** interactive UI validation must be done on a laptop/phone with hardware accel (`./scripts/run-emulator.sh` + `./scripts/smoke-adb.sh`).

## Follow-ups landed from this test pass

- Cache `BlurMaskFilter` / `SweepGradient` in `GuardianScoreView` (DrawAllocation)
- Emulator lite atmosphere (skip grid/radar/corners; fewer motes/ticks)
- `scripts/smoke-adb.sh` for device evidence capture

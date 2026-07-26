# Test evidence — release stabilization

Branch: `fix/release-stabilization`  
Date: 2026-07-26  
Version: **1.0.15** (`versionCode` 16) — source of truth: `gradle/android-app.gradle`

## Validation (this branch)

| Check | Result | Notes |
|-------|--------|-------|
| `./gradlew :app:lintDebug` | **PASS** | Real Android build (default) |
| `./gradlew :app:testDebugUnitTest` | **PASS** | **325** tests |
| `./gradlew :app:assembleDebug` | **PASS** | Real APK ~22 MB; `verifyNoPlaceholderApk` OK |
| Offline `assembleDebug` (`androidBuild=false`) | **FAIL (expected)** | Clear GradleException; no fake APK path |
| `generatePlaceholderArtifact` | **PASS** | Writes `app/build/placeholder-artifacts/` only |
| Secret pattern grep | **PASS** | Only property *names* / examples / scanners (no live keys) |

## Not completed in this environment

| Check | Status |
|-------|--------|
| `:app:lintRelease` / `:app:bundleRelease` with Play signing | Needs `COREGUARD_REQUIRE_RELEASE_SIGNING` + secrets |
| Instrumented ATD job | Workflow added; full emulator run optional (`continue-on-error` in CI until stable) |

## Release freeze

See `docs/RELEASE_FREEZE.md` — no new features until Internal Testing is stable.

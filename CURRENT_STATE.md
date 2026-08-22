# CoreGuard Android Phase-0 Current State

Generated UTC: 2026-08-22T20:43:18Z

## Repository

- Branch: `cursor/phase0-final-instrumentation-gate`
- HEAD (post-merge): `299b819340f15d6d74bb2275c44971141e91f8a1`
- Merged `origin/main` at: `1cf1518203d16d360b91d89da39dc06a9fb503ad`
- Original Phase-0 implementation SHA: `2420f6b127b22e9e6300618bd2e359e8040fa994`
- Modules: `:app`, `:core:model` (`./gradlew projects`)

## Unique delta vs current main

After merging latest `main`, this branch differs from `main` by **docs only**:

- `CURRENT_STATE.md` (this file)

Runtime Phase-0 repairs are already on `main`:

- KVM writable preflight in `.github/workflows/android.yml` (`chmod 0666` + diagnostics)
- Book of Changes append-order chain validation (`ORDER BY rowid`)
- EliteDashboard Compose imports (`mutableStateOf` / `setValue` / `getValue`)
- `MessageDigest` import restore in `QuillaWebSecurityIntelFetcher.kt`

## Build configuration

- compileSdk: `36` (`gradle/android-app.gradle`)
- targetSdk: `36` (`gradle/android-app.gradle`)
- minSdk: `24` (`gradle/android-app.gradle`)
- Android Gradle Plugin: `8.7.3` (`gradle/libs.versions.toml`)
- Gradle: `8.13` (`gradle/wrapper/gradle-wrapper.properties`)
- Kotlin: `1.9.25` (`gradle/libs.versions.toml`)
- Billing dependency: `com.android.billingclient:billing-ktx:7.1.1`
- Emulator/API level: API 36, `CoreGuard_ATD36`

## Verification results (post-merge HEAD `299b819`)

| Gate | Result | Evidence |
|---|---|---|
| merge conflicts | PASS | Resolved; `.github/workflows/android.yml` matches `main` |
| debug compile | PASS | `./gradlew :app:compileDebugKotlin` |
| unit tests | PASS | `./gradlew :app:testDebugUnitTest` — **555** tests, 0 failures |
| EliteDashboard imports | PASS | `mutableStateOf` / `setValue` present |
| MessageDigest import | PASS | present in `QuillaWebSecurityIntelFetcher.kt` |
| BookOfChanges rowid order | PASS | DAO queries use `ORDER BY rowid` |
| API-36 instrumentation (historical) | PASS | CI run `32541991157` on earlier Phase-0 head |
| local emulator in this VM | UNAVAILABLE | container/KVM limits |

## Known warnings (non-blocking)

- AGP 8.7.3 tested through compileSdk 35; app uses compileSdk 36
- Remaining AGP/third-party Gradle 9 deprecations
- Deprecated `QuillaMemoryFactory` call sites

## Remaining blockers (outside Phase-0 code)

- Physical-device matrix
- Play Console / production signing
- External Black Duck backend URL

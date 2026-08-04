# CoreGuard-Android Repository Audit

**Audit date:** 2026-08-04  
**Repository:** `victormart43210-ship-it/CoreGuard-Android`  
**Reviewed branch:** `main`  
**Reviewed head:** `27568f27628f9056cd9e5dacd71cbf3099696bcb`  
**Review type:** Static source, workflow, issue, and pull-request review

> This is an engineering audit, not proof that the app is secure or release-ready.  
> I could not confirm a current successful GitHub Actions run for the reviewed head, and I did not execute the Android build on a physical device during this review.

---

## Executive verdict

CoreGuard has a strong security-first foundation: cleartext traffic is disabled, app backup is disabled, release signing is fail-closed when required, scan stages are engine-driven, scan evidence is being moved into Room, and the release workflow removes the decoded keystore after building.

However, **CoreGuard should not be promoted beyond internal testing until the P0 scanner issues below are fixed and tested**. The most important problems are:

1. The Scanner ViewModel can access Room on the main thread during initialization.
2. The cancellation handler attempts database work from an already-cancelled coroutine.
3. Failed or unavailable data collection can still lead to a `CLEAN` verdict.
4. A corrupt or unknown saved verdict currently defaults to `CLEAN`.

These are truthfulness and reliability problems in the part of the app users will trust most.

---

# 1. P0 — Fix before release testing

## P0.1 — First Scanner opening can hit Room on the main thread

**Files**

- `app/src/main/java/com/coldboar/coreguard/ui/screens/ScannerViewModel.kt`
- `app/src/main/java/com/coldboar/coreguard/mvt/ScanSessionRepository.kt`
- `app/src/main/java/com/coreguard/android/data/local/dao/ScanSessionDao.kt`
- `app/src/main/java/com/coreguard/android/data/local/QuillaDatabase.kt`

**Current behavior**

`ScannerViewModel.init` directly calls:

```kotlin
sessionRepository.ensureLegacyImport()
```

`ensureLegacyImport()` calls synchronous Room DAO functions such as `countSessions()` and `insertSession()`. Room is not configured with `allowMainThreadQueries()`, which is good, but that means the first Scanner initialization can throw a main-thread database-access exception.

**Recommended fix**

Run legacy import on the IO dispatcher:

```kotlin
init {
    viewModelScope.launch(Dispatchers.IO) {
        sessionRepository.ensureLegacyImport()
    }

    ScannerModule.latestReport()?.let {
        _uiState.value = ScannerUiState.Complete(
            it,
            sessionId = "in-memory",
            stageEvents = emptyList()
        )
    }
}
```

A better long-term fix is to make DAO and repository methods `suspend` and keep dispatcher decisions at the repository boundary.

**Required tests**

- Instantiate `ScannerViewModel` with a real Room test database.
- Verify first launch does not throw.
- Verify legacy history imports once.
- Verify a second initialization does not duplicate imported rows.

---

## P0.2 — Cancelled scans may never persist or show the Cancelled state

**File**

- `app/src/main/java/com/coldboar/coreguard/ui/screens/ScannerViewModel.kt`

**Current behavior**

`cancelScan()` cancels `scanJob`. The coroutine catches `CancellationException`, but then calls:

```kotlin
withContext(Dispatchers.IO) {
    sessionRepository.saveSession(...)
}
```

Because the parent coroutine is already cancelled, this `withContext` can immediately throw another `CancellationException`. The cancellation session may not be saved, and the UI may remain stuck in `Scanning` instead of moving to `Cancelled`.

**Recommended fix**

Use `NonCancellable` only for the small cleanup/persistence section:

```kotlin
} catch (ce: CancellationException) {
    val sessionId = withContext(NonCancellable + Dispatchers.IO) {
        sessionRepository.saveSession(
            ScanSessionSaveRequest(
                status = ScanStageId.CANCELLED,
                startedAtMs = startedAt,
                endedAtMs = System.currentTimeMillis(),
                failureReason = "Cancelled by user",
                scannerEngineVersion = ScannerModule.scannerEngineVersion(),
                schemaVersion = ScannerModule.scanSchemaVersion(),
                deepInspectionEnabled =
                    settingsRepository.deepFileInspectionEnabled.first(),
                feedSource = FEED_SOURCE,
                feedVersion = null,
                feedAuthenticity = FEED_AUTHENTICITY,
                feedLoadedAtMs = ScannerModule.iocLoadedAtMs(),
                findings = emptyList(),
                stageEvents = stageEvents.toList()
            )
        )
    }

    withContext(NonCancellable) {
        _uiState.value = ScannerUiState.Cancelled(
            sessionId = sessionId,
            stageEvents = stageEvents.toList(),
            lastCompletedReport = previousCompleted
        )
    }
}
```

Also add a **scan generation ID/token**. Without one, cancelling an old scan and immediately starting another can allow the old cleanup path to overwrite the new scan’s UI state.

Example direction:

```kotlin
private var currentRunId = 0L

fun startScan() {
    val runId = ++currentRunId
    scanJob?.cancel()
    // Only update UI later when runId == currentRunId.
}
```

Do not set `scanJob = null` until the job has actually completed; use `invokeOnCompletion` or a `finally` block.

**Required tests**

- Cancel during package enumeration.
- Cancel during file enumeration.
- Confirm one `CANCELLED` Room session is written.
- Confirm no completed verdict is saved.
- Start a second scan immediately after cancellation and ensure the old scan cannot overwrite the new state.

---

## P0.3 — Unavailable scan data can be reported as CLEAN

**Files**

- `app/src/main/java/com/coldboar/coreguard/mvt/DeviceScanner.kt`
- `app/src/main/java/com/coldboar/coreguard/mvt/NemesisScanner.kt`

**Current behavior**

Package enumeration catches any failure and returns an empty list. Process and file enumeration are also best-effort and can become empty. `NemesisScanner.classify()` returns `CLEAN` whenever there are no detections.

That means:

- package enumeration fails,
- process visibility is unavailable,
- file access fails or is disabled,
- no detections are produced,

and the final result can still say `CLEAN`.

A security scanner must distinguish **“nothing suspicious was found in successfully inspected data”** from **“the data could not be inspected.”**

**Recommended fix**

Introduce collection availability into the report. One safe model is:

```kotlin
enum class CollectionStatus {
    COMPLETE,
    PARTIAL,
    UNAVAILABLE
}

enum class ScanVerdict {
    CLEAN,
    SUSPICIOUS,
    INFECTED,
    INCOMPLETE
}
```

Record status separately for:

- installed packages,
- running processes,
- app-accessible files,
- certificate/signing metadata,
- installer source,
- threat-intelligence feed.

Then classify conservatively:

```kotlin
when {
    criticalFindingExists -> INFECTED
    findings.isNotEmpty() -> SUSPICIOUS
    requiredCollectionFailed -> INCOMPLETE
    else -> CLEAN
}
```

User-facing wording for `INCOMPLETE` should be similar to:

> “The scan finished, but Android restrictions or an error prevented some checks. No threat verdict was issued.”

**Required tests**

- PackageManager throws.
- `/proc` is unreadable.
- file traversal throws.
- IOC feed is empty or unavailable.
- partial visibility never produces a definitive clean verdict.

---

## P0.4 — Corrupt saved verdicts default to CLEAN

**File**

- `app/src/main/java/com/coldboar/coreguard/mvt/ScanHistoryStore.kt`

**Current behavior**

History loading currently uses:

```kotlin
runCatching {
    ScanVerdict.valueOf(obj.optString("verdict", "CLEAN"))
}.getOrDefault(ScanVerdict.CLEAN)
```

An unknown, corrupt, or future verdict value becomes `CLEAN`. That is unsafe fallback behavior for security evidence.

**Recommended fix**

Skip invalid records or map them to an explicit `UNKNOWN`/`INCOMPLETE` state:

```kotlin
val verdictText = obj.optString("verdict", "")
val verdict = runCatching {
    ScanVerdict.valueOf(verdictText)
}.getOrNull() ?: return@mapNotNull null
```

Also synchronize the read-modify-write append operation, or migrate all history writes to Room, because simultaneous app operations can otherwise lose an entry.

**Required tests**

- malformed JSON,
- unknown verdict string,
- missing verdict,
- future enum value,
- concurrent append attempts.

No malformed record should ever be presented as clean.

---

# 2. P1 — High-priority reliability and security improvements

## P1.1 — Durable scan evidence is not written as one transaction

**Files**

- `app/src/main/java/com/coldboar/coreguard/mvt/ScanSessionRepository.kt`
- `app/src/main/java/com/coreguard/android/data/local/dao/ScanSessionDao.kt`

`saveSession()` inserts the session, stages, and findings in separate DAO calls. If one later insert fails, CoreGuard can keep a partial evidence graph.

The DAO already has an `@Transaction` function, but the current repository does not use it. Also, `FindingEvidenceEntity` and `ThreatIntelReferenceEntity` are defined but not populated by `saveSession()`.

**Recommended fix**

- Create one transactional DAO method that:
  1. inserts the session,
  2. inserts findings and captures generated row IDs,
  3. maps evidence and threat references to those row IDs,
  4. inserts stages,
  5. commits everything together.
- Roll back the whole session graph if any insert fails.
- Add database migration and transaction tests.
- Set `exportSchema = true` and commit Room schema JSON for migration review.

---

## P1.2 — Deep inspection and Quilla correlation work in the engine but are disabled in the UI

**Files**

- `app/src/main/java/com/coldboar/coreguard/mvt/ScannerModule.kt`
- `app/src/main/java/com/coldboar/coreguard/ui/screens/ScannerViewModel.kt`
- `app/src/main/java/com/coldboar/coreguard/ui/dashboard/DashboardViewModel.kt`
- `app/src/main/java/com/coldboar/coreguard/ui/dashboard/EliteDashboardScreen.kt`

The scanner now reads `deepFileInspectionEnabled` and `quillaCorrelationEnabled`, and passes them into `ScannerModule`. `ScannerModule` honors both values.

However, the dashboard still labels both controls “NOT YET AVAILABLE” and disables them. The ViewModel comments also incorrectly claim the engine does not honor them.

**Recommended fix**

- Enable the two controls after tests pass.
- Rename “Deep file inspection” to the more accurate:
  - **Inspect app-accessible files**
- Add an explanation that CoreGuard cannot inspect other apps’ private storage without elevated access.
- Keep the threat-intel auto-sync control disabled until WorkManager scheduling is truly implemented.
- Remove stale comments and update release documentation.

---

## P1.3 — IOC feed updater needs redirect, integrity, and atomic-write hardening

**File**

- `app/src/main/java/com/coldboar/coreguard/mvt/IocFeedFetcher.kt`

Good protections already present:

- HTTPS required,
- clear size limit,
- redirects are manually checked,
- response is parsed before being accepted.

Remaining issues:

1. Redirects are recursive with no maximum hop count.
2. `301..308` includes statuses that should not all be treated as ordinary redirects.
3. The new feed is written directly over the active file.
4. HTTPS authenticates transport, but the feed has no signed manifest, anti-rollback protection, or expiry policy.
5. Any HTTPS host can be passed to the public method.

**Recommended fix**

- Permit only `301`, `302`, `303`, `307`, and `308`.
- Limit redirects to five.
- Resolve relative `Location` headers safely.
- Download to a temporary file.
- Verify schema, hash, version, expiry, and signature.
- `fsync` and atomically rename the verified file.
- Keep the previous verified feed for rollback.
- Use a production host allowlist unless a debug-only custom-feed feature is intentional.

Do not rely on TLS certificate pinning as the only authenticity control. A signed feed manifest is easier to rotate safely.

---

## P1.4 — Instrumented tests currently do not block CI

**File**

- `.github/workflows/android.yml`

The emulator gate uses `continue-on-error: true`. A test regression and an infrastructure failure can both become warnings while the overall job remains successful.

**Recommended fix**

Split this into two gates:

1. **Required deterministic gate**
   - unit tests,
   - lint,
   - assemble,
   - database migration tests,
   - pure scanner tests.

2. **Required release-promotion device gate**
   - KVM-capable emulator or physical device,
   - Scanner cancellation,
   - Room persistence,
   - VPN lifecycle,
   - notification-listener opt-in,
   - billing license-test flow.

A flaky public runner may remain non-blocking for ordinary commits, but the same tests must pass on a reliable runner before Internal/Closed Testing promotion.

---

## P1.5 — Current head does not have verified green status in this audit

No current status/check records or workflow runs were returned for the reviewed head during this audit. This does not prove CI is broken, but it means the repository should not describe the current head as green without opening Actions and verifying it.

**Action**

Run or re-run:

```bash
./gradlew \
  :core:model:test \
  :app:testDebugUnitTest \
  :app:lintDebug \
  :app:assembleDebug \
  :app:assembleRelease \
  --stacktrace
```

Then run the emulator/device gate:

```bash
HEADLESS=1 ./scripts/quilla-emulator-tests.sh
```

Record the run URL and exact commit SHA in `COREGUARD_TEST_EVIDENCE.md`.

---

# 3. Pull-request and issue review

## PR #79 — Guardian Intelligence Phases 1–10

**Status:** Open, not mergeable, and heavily diverged from current `main`.  
At audit time it was approximately **3 commits ahead and 117 commits behind** current `main`.

The branch adds a second large truth/evidence architecture, Guardian database, Truth Seal, and UI surfaces. Current `main` now contains later shared-truth, Room, scanner, and Guardian-related work, so merging this branch as-is risks:

- duplicate model types,
- duplicate Truth Seal implementations,
- conflicting Room databases,
- conflicting dashboard/navigation code,
- old assumptions replacing newer scanner behavior.

**Recommendation**

Do not merge PR #79 as-is.

Create a new branch from current `main`, compare feature-by-feature, and cherry-pick or rewrite only genuinely missing parts. Good candidates to evaluate independently are:

- installation verification,
- Ward Circle hardening checks,
- redacted report builder,
- hash-chain tests.

Each should be a small PR with current tests and no duplicate model layer.

---

## PR #75 — Nemesis to Quilla/choir bridge

**Status:** Open, not mergeable, and heavily diverged from current `main`.  
At audit time it was approximately **2 commits ahead and 119 commits behind** current `main`.

The old branch modifies Scanner history, Quilla memory, the VPN, forensic journal, and UI. Current `ScannerViewModel` already records scan history. The old PR also makes `ScannerModule` record history, which could create duplicate timeline entries if merged without adaptation.

**Recommendation**

Do not merge PR #75 as-is.

Create a focused replacement PR from current `main` that adds only the desired post-scan event bridge. It should:

- receive an immutable completed `ScanReport`,
- run only after successful session persistence,
- be idempotent by `sessionId`,
- never change the scan verdict,
- never duplicate scan history,
- respect `quillaCorrelationEnabled`,
- avoid mystical labels in technical evidence fields.

---

## Issue #92 — Documentation/state mismatch

GitHub currently shows issue #92 as open with the title `c` and body:

```text
guardian-integrated-v1.0.18 → main
```

However, `COREGUARD_BLOCKERS.md` says issue #92 was closed.

**Recommendation**

Either close issue #92 with a clear resolution comment, or rename it and describe remaining work. Update `COREGUARD_BLOCKERS.md` so repository documentation matches GitHub.

---

## PR #91 — ChatGPT Action workflow

This PR was closed without merging. Keep it closed. It combined two third-party action declarations into malformed workflow text and would introduce unnecessary secret and supply-chain risk.

Any future AI-assisted workflow should:

- use a maintained action,
- pin it to a full commit SHA,
- grant minimum permissions,
- never expose API keys to untrusted pull requests,
- avoid automatic write access to production branches.

---

# 4. Documentation and configuration drift

## D1 — Release readiness still mentions a removed permission

`docs/RELEASE_READINESS.md` says `READ_PHONE_STATE` must be declared to Play, but the current manifest does not request it.

**Fix:** Remove the stale declaration from the guide and ensure the Play Console form matches the actual merged manifest.

## D2 — Version evidence is stale

The build configuration is currently:

```text
versionCode 18
versionName 1.0.17
```

The release-readiness checklist still references a successful AAB for `v1.0.15 / versionCode 16`.

**Fix:** Do not mark a newer release build complete based on an older artifact. Attach evidence for the exact current commit and version.

## D3 — Project decisions and implementation do not match

Current build configuration still uses:

```text
compileSdk 35
targetSdk 35
Play Billing 7.1.1
monthly SKU only
```

The project’s locked plan calls for:

```text
compileSdk 36
targetSdk 36
Play Billing 9.1.0
monthly + yearly SKU
7-day trial on yearly
```

**Fix:** Handle these in a dedicated dependency/product PR. Do not combine the SDK, Billing, and pricing migration with scanner bug fixes.

## D4 — Dashboard comments are behind the implementation

The dashboard ViewModel and UI describe deep inspection and Quilla correlation as unimplemented, while the current Scanner path already honors them.

**Fix:** Update comments, labels, tests, and docs in the same PR that enables the controls.

## D5 — Release workflow publishes the AAB publicly

Tagged releases upload the AAB to a public GitHub Release. This is not automatically a cryptographic vulnerability, but it should be an explicit distribution decision.

**Fix options**

- Keep public artifacts and document that decision.
- Publish checksums/attestations publicly but keep the upload AAB in protected Actions artifacts.
- Use a separate private release-delivery process for Play upload files.

---

# 5. P2 — Architecture and maintainability improvements

## P2.1 — Move device metric file reads off the main thread

`DashboardViewModel.updateDeviceMetrics()` reads `/proc/stat` and memory information while called from a Compose `LaunchedEffect` on the main dispatcher.

Use `Dispatchers.IO` and update state afterward to reduce UI jank.

## P2.2 — Remove the duplicate metrics-loop implementation

There is a loop in `EliteDashboardScreen` and another `startMetricsLoop()` in `DashboardViewModel`. Keep one owner for polling, preferably the ViewModel, and prevent multiple loops from being launched.

## P2.3 — Complete Hilt migration incrementally

Manual factories are acceptable temporarily, but dependency construction is spreading across UI classes.

Suggested sequence:

1. Application + database modules.
2. repositories.
3. ScannerViewModel.
4. DashboardViewModel.
5. remaining screens.

Do not perform a repository-wide Hilt conversion in the same PR as security behavior changes.

## P2.4 — Modernize Gradle plugin configuration

The project mixes `buildscript/apply plugin` with the `plugins` block in another module, producing a Kotlin Gradle plugin dual-load warning.

Move plugin versions to `settings.gradle`, a version catalog, or root plugin declarations. Validate this in CI because plugin changes can break every build task.

## P2.5 — Add Room schema export and migration tests

For durable security evidence:

- set `exportSchema = true`,
- configure `room.schemaLocation`,
- commit schemas,
- test migrations from every shipped version,
- verify foreign keys and deletion behavior.

## P2.6 — Add a real scanner integration test seam

Current `ScannerViewModelTest` mainly tests sealed-state objects and does not instantiate the ViewModel.

Inject interfaces for:

- scanner engine,
- session repository,
- settings repository,
- clock,
- dispatcher provider.

Then test full state transitions without needing a real device for every case.

---

# 6. Recommended implementation order

## PR A — Scanner reliability P0

- Move legacy import off main.
- Fix cancelled-coroutine persistence.
- Add run-generation protection.
- Stop invalid history from defaulting to clean.
- Add integration-focused ScannerViewModel tests.

## PR B — Truthful incomplete-scan state

- Add per-source collection status.
- Add `INCOMPLETE` or equivalent no-verdict state.
- Update persistence, UI, exports, and migration handling.
- Add PackageManager/process/file failure tests.

## PR C — Atomic evidence persistence

- Save session graph transactionally.
- Persist evidence rows and threat-intel references.
- Export Room schemas.
- Add rollback/migration tests.

## PR D — Settings and documentation alignment

- Enable app-accessible-file and Quilla correlation controls.
- Keep auto-sync disabled until scheduling exists.
- Correct stale comments, permissions, versions, issue status, and release evidence.

## PR E — Threat-feed authenticity

- Bounded redirects.
- Temp file + atomic replacement.
- Signed manifest, version, expiry, anti-rollback.
- Last-known-good feed recovery.
- Feed-integrity tests.

## PR F — Release dependency/product migration

- SDK 36.
- Billing 9.1.0.
- monthly and yearly products.
- yearly trial offer.
- complete purchase lifecycle and server-side verification plan.
- real Play license-tester evidence.

---

# 7. Minimum release gate

Do not promote beyond Internal Testing until all are true:

- [ ] P0.1 main-thread Room access fixed.
- [ ] P0.2 cancellation persistence and race fixed.
- [ ] P0.3 incomplete data cannot become CLEAN.
- [ ] P0.4 corrupt history cannot become CLEAN.
- [ ] Current commit passes unit tests, lint, debug build, and release build.
- [ ] Room migration and transaction tests pass.
- [ ] Scanner cancellation passes on a physical device.
- [ ] VPN starts, stops, and recovers correctly on a physical device.
- [ ] Notification Listener is opt-in and correctly disclosed.
- [ ] Signed AAB was built from the exact release commit.
- [ ] R8/minified build was installed and smoke-tested.
- [ ] Play Billing was tested with a license tester.
- [ ] Privacy policy and Data Safety answers match actual network and permission behavior.
- [ ] Open stale PRs are closed or replaced with current-main PRs.

---

# 8. Commands to add this audit to the repository from a Chromebook

## Recommended: push it on a new branch

After downloading this file, use the ChromeOS Files app to move it into:

```text
Linux files/CoreGuard-Android
```

Then open the Linux terminal and run:

```bash
cd ~/CoreGuard-Android

git pull --ff-only origin main
git checkout -b docs/repository-audit-2026-08-04

git status
git add COREGUARD_REPOSITORY_AUDIT_2026-08-04.md
git commit -m "docs: add CoreGuard repository audit"
git push -u origin docs/repository-audit-2026-08-04
```

GitHub should then offer a button to create a pull request.

## Terminal copy from ChromeOS Downloads

If the file is in your Chromebook Downloads folder:

```bash
cp /mnt/chromeos/MyFiles/Downloads/COREGUARD_REPOSITORY_AUDIT_2026-08-04.md \
  ~/CoreGuard-Android/
```

Then run the branch, add, commit, and push commands above.

---

# Final assessment

CoreGuard is becoming a substantial application, and several recent changes improved build hardening, evidence labeling, scanner stages, and release hygiene. The next best move is not adding more features. It is to stabilize the Scanner’s database, cancellation, unavailable-data, and history semantics, then prove those fixes with repeatable tests.

**Current recommendation:** suitable for continued development and controlled internal testing after P0 fixes; not ready for a public security-product claim or production release yet.

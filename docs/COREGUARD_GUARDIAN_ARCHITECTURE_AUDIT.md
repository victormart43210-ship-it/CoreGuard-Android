# CoreGuard Guardian Architecture Audit (Phase 0)

**Blueprint:** [`docs/COREGUARD_GUARDIAN_BLUEPRINT.md`](COREGUARD_GUARDIAN_BLUEPRINT.md)  
**Audit head:** `626962f85fbde529972ae58eb2fcf75e25af841e` (`main` at Phase 0)  
**Branch:** `cursor/guardian-phase0-audit-6db1`  
**Scope:** Documentation and inventory only — **no product behavior changes**, no Phase 1 types yet.

This audit maps the live repository to the Guardian Intelligence Blueprint so Phase 1 can adapt to existing names instead of inventing duplicates.

---

## 1. Current architecture

### 1.1 Gradle modules

| Module | Path | Role |
|--------|------|------|
| `:app` | `app/` | Compose UI, evaluators, Quilla, Elite, Swarm, MVT/Nemesis, billing, JNI host |
| `:core:model` | `core/model/` | Pure JVM domain: `SecurityCheck*` + `GuardianScore` / `EvidenceKind` |

Feature Gradle modules (`:feature:*`) are **planned** in [`MODULE_ARCHITECTURE.md`](MODULE_ARCHITECTURE.md) but not extracted yet. Soft façades inside `:app` already exist (`ScannerModule`, `ShieldModule`, `BillingModule`, `SwarmModule`, `EliteModule`).

### 1.2 Layering today (approximate)

```text
Compose UI (ui/screens, ui/dashboard, ui/components)
    ↓  (often direct)
Module façades (ScannerModule, EliteModule, SwarmModule, BillingModule, ShieldModule)
    ↓
Engines / evaluators / Quilla agents
    ↓
Android APIs · SharedPreferences · encrypted journal file · Room (Quilla only) · JNI TamperGuard
```

Blueprint target (ViewModels → use cases → domain → repositories) is **aspirational**. Home and Tools still call façades and runners from composables. Migrate incrementally; do not big-bang reorganize packages.

### 1.3 Package roots (do not blindly rename)

| Root | Contents |
|------|----------|
| `com.coldboar.coreguard` | Primary app code (UI, elite, mvt, swarm, defense, billing, …) |
| `com.coldboar.coreguard.quilla` | Quilla agent, correlator, intel, Infinity trainer |
| `com.quilla.intelligence.sdk` | STIX fetchers + sliding-window correlation SDK |
| `com.coreguard.android.data.local` | Room `QuillaDatabase` |
| `com.coreguard.security.telemetry` | Signed telemetry deltas / ring buffer |

Three Quilla-related roots already documented as a future unification risk (`:feature:quilla` + `:data:quilla`).

---

## 2. Existing reusable components

### 2.1 Security detectors (wired in `SecurityCheckRunner`)

Source: `app/src/main/java/com/coldboar/coreguard/SecurityCheckRunner.kt`

| Evaluator | File |
|-----------|------|
| Debugger / Emulator / Root / Build / Signature | `SecurityCheckEvaluators.kt` |
| Frida / Native debugger / Hooks / Mount / Memory | `TamperEvaluators.kt` |
| StrongBox | `HardwareKeyManager.kt` (`StrongBoxCheckEvaluator`) |
| Process lineage | `ProcessLineageEvaluator.kt` |
| Spyware scan bridge | `SpywareScanEvaluator.kt` |
| Overlay / Accessibility / Sideload | `defense/IntrusionDefenseEvaluators.kt` |

**Not wired into the runner (exists separately):**

- `attestation/AttestationEvaluator.kt` + `PlayIntegrityAttestation.kt`
- `BehavioralAnomalyEngine.kt`
- Elite `DynamicThreatEngine` / `ScamGuardEngine`
- Nemesis `DeviceScanner` / `NemesisScanner` (via `ScannerModule`)

Contract: `core/model/.../SecurityChecks.kt` — `SecurityCheckEvaluator` → `SecurityCheckResult` (`PASS` / `WARN` / `FAIL`).

### 2.2 Score / evidence (partial honesty model)

| Type | Path | Notes |
|------|------|-------|
| `GuardianScore` / `GuardianRank` | `core/model/.../GuardianScore.kt` | 0–100 + plain-language ranks |
| `EvidenceKind` | same | `VERIFIED` / `HEURISTIC` / `EDUCATIONAL` — closest to blueprint `EvidenceClass` / `Confidence` blend |
| `GuardianScoreEvidence` | same | Per-check explanation + recommended action string |
| Home confidence labels | `ui/dashboard/EliteDashboardScreen.kt` | Maps `EvidenceKind` for display |

### 2.3 Persistence

| Store | Mechanism | Path |
|-------|-----------|------|
| Quilla hypotheses | Room | `com/coreguard/android/data/local/QuillaDatabase.kt` |
| Scan history | SharedPreferences | `mvt/ScanHistoryStore.kt` |
| Forensic journal | Encrypted file + hash chain | `elite/ForensicJournal.kt` |
| Infinity training ledger | SharedPreferences | `quilla/QuillaInfinityTrainer.kt` |
| Telemetry | In-memory ring | `com/coreguard/security/telemetry/` |
| Local wipe | Orchestration | `LocalSecurityData.kt` |

### 2.4 Quilla

Key types under `app/.../quilla/`: `UltimateQuillaAgent`, `QuillaMemoryFactory`, `QuillaCorrelationEngine`, `QuillaIntelNetwork`, `QuillaInfinityTrainer`, `QuillaIocBridge`, `QuillaPriorityEngine`, `QuillaQuantumCorrelate` (classical, labeled). UI: `ui/components/QuillaAgentPanel.kt`. Lore: `lore/QuillaLivingGeometry.kt`, `lore/QuillaKnowledge.kt`.

### 2.5 Native TamperGuard

- JNI: `NativeTamperGuard.kt`
- Native: `app/src/main/cpp/tamperguard.cpp` + `CMakeLists.txt`
- Boot: `CoreGuardApplication` loads library / baseline early

### 2.6 Billing (fail-closed)

`BillingModule` → `BillingProvider` / `PlayBillingProvider` / `FailClosedBillingProvider` / `DemoBillingProvider` (tests only) / `EntitlementPolicy`.

### 2.7 Simulation / educational (must stay labeled)

| Surface | Path |
|---------|------|
| Network Defense Lab | `lab/NetworkDefenseLabActivity.kt`, `SimulationEngine.kt`, `docs/NETWORK_DEFENSE_LAB.md` |
| MASVS educational scoring | `compliance/MasvsComplianceScorer.kt`, `ComplianceScreen.kt` |
| Quilla “quantum” correlate | classical simulation labels in code/docs |
| Demo billing | `DemoBillingProvider.kt` |

### 2.8 Design system

- Theme: `ui/theme/{Theme,Color,Type,Shape}.kt` (Syne + Manrope — see `docs/FONTS.md`)
- Elite palette: `ui/dashboard/ElitePalette.kt`
- Atmosphere / HUD: `AtmosphereBackground.kt`, `HudChrome.kt`, brand components

Blueprint “Silent Sigil” (Cinzel ceremonial serif, category glyphs) is **not** fully implemented; do not rip out Syne/Manrope in Phase 1.

### 2.9 Compose surfaces

**Routes:** `ui/navigation/CoreGuardRoute.kt`  
**Screens:** Home, Onboarding, Scanner, Timeline, Tools, Shield, Settings, SupplyChain, Compliance, PrivacyPolicy, Overlay Matrix, Forensic Journal, Scam Guard, Secret Portal.  
**Primary Home:** `EliteDashboardScreen.kt` (status → next action → attention → shortcuts).  
**Counters:** `SwarmAlertCounter` + Redux stores (`SwarmAlertCounterStore`, `EliteThreatCounterStore`).

### 2.10 Claim / threat documentation (already present)

| Doc | Role vs blueprint |
|-----|-------------------|
| `docs/SECURITY_CLAIMS.md` | Claims matrix (blueprint §18 `CLAIMS_MATRIX.md` — **reuse this name**, do not duplicate) |
| `docs/THREAT_MODEL.md` | Exists (draft) — extend rather than recreate |
| `docs/MODULE_ARCHITECTURE.md` | Module / Redux Counter contract |
| `docs/SWARM_ARCHITECTURE.md` | Swarm vs RASP boundaries |

---

## 3. Gaps against the Guardian Intelligence Blueprint

| Blueprint concept | Status | Closest analog | Gap |
|-------------------|--------|----------------|-----|
| `EvidenceClass` (OBSERVED/INFERRED/SIMULATED/UNAVAILABLE/USER_REPORTED) | **Missing** | `EvidenceKind` (3 values) | Need distinct taxonomy; map VERIFIED→OBSERVED-ish, HEURISTIC→INFERRED, EDUCATIONAL→SIMULATED carefully |
| Calm `Severity` (Protected…High Confidence Risk) | **Missing** | `SecurityCheckState`, DTS `Band`, `ThreatSeverity`, `SwarmSeverity`, `RiskSeverity` | Fragmented; Phase 1 should introduce calm Severity without deleting check PASS/WARN/FAIL |
| `Confidence` enum | **Missing** | `EvidenceKind` reused as “confidence” in UI | Separate confidence from evidence class |
| `SecurityFinding` + `Evidence` + `RecommendedAction` | **Missing** | `SecurityCheckResult` + `GuardianScoreEvidence` | Adapter layer required |
| `TruthSeal` Compose | **Missing** | Honesty captions / EvidenceKind chips | New reusable component |
| `OracleEngine` | **Missing** | Quilla agent + static explanations on checks | Phase 2 |
| `GuardianPulse` / `GuardianStateResolver` | **Missing** | Guardian Score + DTS + hub status | Phase 3 |
| Book of Changes timeline | **Missing** (name) | `TimelineScreen` + `ScanHistoryStore` | Broader event model + Room (Phase 4) |
| Evidence Constellation rules | **Partial** | Quilla correlators | Need versioned conservative rules + UI narrative |
| Quilla Private Baseline | **Partial** | Native text baseline; “establish baseline” copy | No first-class `DeviceBaseline` learning mode |
| Ward Circle hardening journey | **Partial** | `DeviceHardeningGuide` / Settings tips | Not a completion-ring journey |
| Ritual of Response | **Missing** | Home next-action CTAs | Phase 8 |
| Verify CoreGuard (in-app) | **Partial** | `SignatureCheckEvaluator` + docs attestation | No dedicated Verify screen / `InstallationVerification` model |
| Silent Sigil design system | **Partial** | Elite theme + geometry | Glyph + Cinzel system deferred |
| Presentation ViewModels | **Mostly missing** | Compose calls façades | Incremental |

---

## 4. Exact proposed file paths for Phase 1

Phase 1 = shared truth model + TruthSeal + adapters. **No** Oracle, timeline, correlation UI, baseline, or visual redesign.

Place new **domain types** in `:core:model` (JVM, unit-testable, no Android deps):

```text
core/model/src/main/kotlin/com/coldboar/coreguard/guardian/
  EvidenceClass.kt          # enum
  Severity.kt               # calm user-facing severity (name may be GuardianSeverity if clash)
  Confidence.kt             # enum
  Evidence.kt               # data class
  RecommendedAction.kt      # + ActionType
  SecurityFinding.kt        # data class
  FindingCategory.kt        # enum (subset OK in Phase 1)
```

Adapters (Android-aware mapping from existing detectors) in `:app`:

```text
app/src/main/java/com/coldboar/coreguard/guardian/
  SecurityCheckFindingAdapter.kt   # SecurityCheckResult (+ GuardianScoreEvidence) → SecurityFinding
  EvidenceClassMapper.kt           # EvidenceKind / check id → EvidenceClass + Confidence
```

Truth Seal UI:

```text
app/src/main/java/com/coldboar/coreguard/ui/components/TruthSeal.kt
app/src/test/java/com/coldboar/coreguard/guardian/SecurityCheckFindingAdapterTest.kt
app/src/test/java/com/coldboar/coreguard/guardian/EvidenceClassMapperTest.kt
# Optional androidTest semantics:
app/src/androidTest/java/com/coldboar/coreguard/guardian/TruthSealSemanticsTest.kt
```

Docs / changelog (Phase 1):

```text
docs/SECURITY_CLAIMS.md          # extend rows for Truth Seal language (do not add CLAIMS_MATRIX.md)
CHANGELOG.md
README.md                        # short pointer to blueprint + audit
docs/MODULE_ARCHITECTURE.md      # note guardian/ package
```

**Reuse, do not duplicate:**

- Keep `EvidenceKind` until adapters stabilize; deprecate or wrap later.
- Keep `SECURITY_CLAIMS.md` as the claims matrix (`CLAIMS_MATRIX.md` alias not required).
- Keep `THREAT_MODEL.md`; extend in later phases.
- Do not create a second Guardian Score calculator.

**Optional feature flag** (if Home wiring is risky):  
`BuildConfig` / prefs flag `guardianTruthSealEnabled` — default off until adapters cover all runner checks.

---

## 5. Migration risks

| Risk | Mitigation |
|------|------------|
| Confusing `EvidenceKind` with new `EvidenceClass` | Explicit mapper + dual display only where needed; unit tests for every mapping |
| Overclaiming when adapting FAIL → High Confidence Risk | Cap confidence for heuristic detectors; signature mismatch can be higher |
| Package rename churn (three Quilla roots) | **Do not rename** in Phase 0–1 |
| Home copy already uses dramatic ranks (`BREACHED`) | Phase 1 does not reword Home; Phase 3 Pulse adopts calm severity language |
| Room timeline vs prefs scan history | Phase 4 only; leave `ScanHistoryStore` alone in Phase 1 |
| Redux Counter PRs (#75/#77) in flight | Phase 1 should rebase onto latest `main`; avoid conflicting Elite Home edits |
| Dependency Graph still disabled | Soft-fail Dependency Review is environmental — not a Phase 0 blocker |
| README still says CPU is simulated while `CpuUsageCalculator` reads `/proc` | Doc honesty fix can ride a later phase; do not invent claims |

---

## 6. Test baseline (Phase 0)

Recorded on head `626962f` with `./gradlew -Pcoreguard.androidBuild=true`:

| Command | Result |
|---------|--------|
| `:app:testDebugUnitTest` | **PASS** — **333** tests, 0 failures |
| `:app:lintDebug` | **PASS** — 0 Error/Fatal; 71 Warning + 3 Information |
| `:app:assembleDebug` | **PASS** |
| `:app:verifyNoPlaceholderApk` | **PASS** — real APK **22,722,502** bytes |

Instrumentation / emulator gate not re-run for this docs-only Phase 0 (optional; CI covers on PR).

---

## 7. Phase 1 implementation checklist

Use only after this Phase 0 PR is reviewed. Do **not** start Oracle / Pulse / timeline here.

- [ ] Add `guardian/` types to `:core:model` (`EvidenceClass`, calm `Severity`, `Confidence`, `Evidence`, `RecommendedAction`/`ActionType`, `SecurityFinding`, `FindingCategory` subset).
- [ ] Document mapping table: each `SecurityCheckRunner` evaluator → default `EvidenceClass` + max `Confidence` + calm `Severity` policy.
- [ ] Implement `SecurityCheckFindingAdapter` + `EvidenceClassMapper` with unit tests for **all** wired evaluators (including PASS/WARN/FAIL and educational-adjacent ids).
- [ ] Add Compose `TruthSeal` with text labels, contentDescription, non-color-only distinction, optional info dialog.
- [ ] Optionally show `TruthSeal` on one existing surface (e.g. Needs Attention rows or a finding detail stub) behind a feature flag — **without** changing detector outcomes.
- [ ] Extend `docs/SECURITY_CLAIMS.md` for Truth Seal wording; add CHANGELOG entry.
- [ ] Run `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug`, `:app:verifyNoPlaceholderApk`.
- [ ] Add androidTest semantics for TruthSeal TalkBack labels if Compose UI test harness is available.
- [ ] Explicitly **out of scope:** OracleEngine, GuardianPulse, Book of Changes Room schema, constellation rules, baseline learning, Ward Circle, Ritual of Response, Verify screen redesign, Silent Sigil typography swap.

---

## 8. What is real vs inferred vs simulated (current honesty stance)

| Class | Examples in repo today |
|-------|------------------------|
| **Observed / verified-leaning** | Signing cert SHA-256 vs expected pin; StrongBox capability; package/signing APIs; VPN user consent state |
| **Inferred / heuristic** | Root paths, Frida ports, hook libs, overlay/accessibility abuse heuristics, Nemesis IOC matches, DTS band, swarm WARN+ |
| **Simulated / educational** | Network Defense Lab; MASVS educational scorer; Quilla quantum-*inspired* correlate; DemoBilling |
| **Unavailable** | Many OS internals without permission; Play Integrity not in default runner |
| **User-reported** | Manual Scam Guard paste; trusted-app exceptions (future baseline) — not a first-class type yet |

Phase 1 must encode these distinctions in data, not only in prose.

---

## 9. Phase 0 deliverables (this PR)

1. `docs/COREGUARD_GUARDIAN_BLUEPRINT.md` — checked-in blueprint.  
2. `docs/COREGUARD_GUARDIAN_ARCHITECTURE_AUDIT.md` — this file.  
3. Baseline build/test/lint numbers above.  
4. **No** Phase 1 code, package renames, or security/CI weakening.

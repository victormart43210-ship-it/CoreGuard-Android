# Changelog

All notable changes to CoreGuard-Android are documented here.
Version numbers match `gradle/android-app.gradle`.

## Unreleased

<<<<<<< HEAD
### Guardian Intelligence (Phases 1–10)

- Shared truth model in `:core:model` (`EvidenceClass`, calm `Severity`, `Confidence`, `SecurityFinding`, …)
- Deterministic Oracle Engine + Truth Seal + Guardian Pulse
- Book of Changes (Room + SHA-256 hash chain), Evidence Constellation (3 conservative rules)
- Ward Circle hardening journey, Quilla Private Baseline (7-day learning), Ritual of Response
- Verify CoreGuard installation identity, redacted report export
- Hub screen: Tools → **Guardian Intelligence** (`GuardianModule` façade)
- Home shows Guardian Pulse (tap opens Intelligence hub)
- Baseline prefs use `PREF_*` names (MASVS-CRYPTO-1 false-positive fix)
- On-device smoke: `GuardianIntelligenceOnDeviceTest` in emulator gate
- Docs: blueprint + Phase 0 architecture audit
=======
### Android 16 compatibility pass (PR-002 scope)

- Enabled edge-to-edge setup in `MainActivity` (`enableEdgeToEdge()`) for target-36 system bar behavior.
- Explicitly enabled predictive back callbacks via `android:enableOnBackInvokedCallback="true"` in manifest.
- Added `docs/ANDROID_16_COMPATIBILITY.md` with evidence-classed validation status (`OBSERVED` / `INFERRED` / `UNAVAILABLE`).
- No billing/backend/IOC/Guardian feature redesign in this slice.
>>>>>>> origin/main

### Docs — Guardian Intelligence Phase 0

- Added `docs/COREGUARD_GUARDIAN_BLUEPRINT.md` (product/architecture roadmap)
- Added `docs/COREGUARD_GUARDIAN_ARCHITECTURE_AUDIT.md` (repo map, gaps, Phase 1 paths, test baseline)

## 1.0.17 — 2026-07-26

### Nemesis ↔ Quilla ↔ choir bridge

- Every Nemesis scan completes through `QuillaMemoryModule.onScanCompleted` (hypotheses, correlator, choir refresh, Elite DTS)
- Non-clean / hit scans append `NEMESIS_SCAN` forensic journal entries and WARN+ swarm alerts
- Scanner UI shows Quilla/choir bridge note; history always persisted inside `ScannerModule`
- Tzadkiel/Gabriel blessings reflect Nemesis Memory; SpywareScan falls back to scan history after process death
- `QuillaMemoryModule` is the module-pattern façade (`QuillaMemoryFactory` remains a deprecated alias)
- Security Swarm VULN-IMPLICIT exempts only MAIN+LAUNCHER exported activities (MainActivity false positive)

### Final release polish + Quilla mini-game

- Live Security Score on Home, threat timeline visualization, premium motion with reduced-motion freeze
- Hourly battery-not-low Security Pulse (WorkManager) for background score refresh
- Production screens require explicit billing/nav wiring (no demo defaults)
- Claims honesty: scanner indicator language, Shield copy, README CPU BASIC `/proc/stat`
- Emulator/smoke install hardened (`-d` + uninstall retry; async launch + pid poll)
- Hidden Quilla purge mini-game: Settings → About → Version ×7
- MASVS false-positive cleanup for pulse prefs (`PREF_*`)

## 1.0.16 — 2026-07-26

### Quilla Infinity threat training

- Uncapped on-device study of malware + vulnerability corpora (CISA KEV, MISP Android, MISP Malpedia mobile filter, expanded Amnesty/MVT STIX)
- `QuillaInfinityTrainer` hardens angel dossiers + notifies swarm peers after each Intel sync (not cloud LLM weights)
- Local “train infinity angels” path studies the bundled Cyber Codex without HTTPS
- Honesty preserved: Research ≠ Nemesis Premium signatures; training improves correlation/teaching, not zero-day guarantees

## 1.0.15 — 2026-07-26

### Home clarity

- Elite Home leads with plain-language Guardian status, one primary next step, and FAIL/WARN evidence (confidence labeled) instead of lore-as-proof
- Sacred geometry called out as brand artwork, not a sensor reading
- Dynamic Threat Score caption states on-device correlator / not cloud AI
- Empty states polished on Scanner, Forensic Journal, and Scam Guard; shortcut a11y descriptions improved

### CI

- Dependency Review soft-fails when GitHub Dependency Graph is disabled (environmental); enable the graph to get real review results

## 1.0.14 — 2026-07-26

### Release stabilization

- Real Android Gradle build is the **default**; placeholder APK path removed from `assembleDebug`
- Explicit `generatePlaceholderArtifact` writes under `build/placeholder-artifacts/` only
- Partial signing configuration fails fast; release CI requires signing secrets
- R8 keep rules expanded (Billing, Room, JNI, VPN, NotificationListener, Elite)
- Fail-closed billing when Play Billing is unavailable
- User wipe for local security data (scan history, forensic journal, IOC feeds, Quilla DB)
- Scam Guard journal stores host/score/reasons — not full notification bodies or URL paths
- Documentation corrected for version, signing env vars, instrumentation tests, network behavior
- CI: placeholder rejection, release mapping/native symbols upload, dependency review

### Already in 1.0.14 product surface

- Elite Dynamic Threat Score, Overlay Matrix, Forensic Journal, Scam Guard
- Notification Listener disclosure + VPN/Shield disclosures
- Quilla on-device correlator (classical / quantum-inspired — not a cloud LLM)

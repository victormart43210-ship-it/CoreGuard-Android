# Changelog

All notable changes to CoreGuard-Android are documented here.
Version numbers match `gradle/android-app.gradle`.

## 1.0.15 — 2026-07-26

### Home clarity

- Elite Home leads with plain-language Guardian status, one primary next step, and FAIL/WARN evidence (confidence labeled) instead of lore-as-proof
- Sacred geometry called out as brand artwork, not a sensor reading
- Dynamic Threat Score caption states on-device correlator / not cloud AI
- Empty states polished on Scanner, Forensic Journal, and Scam Guard; shortcut a11y descriptions improved

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

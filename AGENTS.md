# AGENTS.md

## Cursor Cloud specific instructions

### Current repository state
Native Kotlin Android project for **CoreGuard** (`com.coldboar.coreguard`).
Gradle wrapper is present (`./gradlew`). Primary module: `:app`.

### Toolchain
- **JDK**: Prefer **17** for AGP 8.5 / project `jvmTarget` (VM may also have JDK 21).
- **Build**: `./gradlew test`, `./gradlew assembleDebug`, `./gradlew bundleRelease`
- **Lint**: `./gradlew lint` when Android SDK is available
- Android SDK (`ANDROID_HOME`) is required for assemble/lint; unit tests that
  avoid Android framework APIs can run with less SDK surface once dependencies
  resolve.

### Honesty constraints for agents
- Do not invent Play Billing, purchase verification, or Play approval claims.
- Keep CPU labeled as simulated unless a real implementation is added.
- Never commit secrets, keystores, or signing passwords.
- Keep `DemoBillingProvider` clearly separate from any real billing path.

### Docs
- `docs/THREAT_MODEL.md` — assets, adversaries, threats, mitigations, residual risk
- `docs/RELEASE_READINESS.md` — signing, AAB, Play Console, Data Safety, billing, tracks

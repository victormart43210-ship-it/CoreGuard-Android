# CoreGuard Guardian Intelligence Blueprint
## Cursor-Ready Product, Architecture, UX, and Implementation Specification

**Repository:** `victormart43210-ship-it/CoreGuard-Android`  
**Package:** `com.coldboar.coreguard`  
**Primary platform:** Native Android, Kotlin, Jetpack Compose  
**Document purpose:** Give Cursor a precise, safe, production-minded roadmap for evolving CoreGuard into a trustworthy, explainable, visually distinctive security companion.

---

# 1. Mission

CoreGuard should feel like a calm, intelligent guardian.

It should:

1. Observe meaningful device-security signals.
2. Explain what each signal means in plain language.
3. Clearly separate direct observations from inferences and simulations.
4. Track changes over time.
5. Correlate weak signals without exaggerating certainty.
6. Give the user one clear next action.
7. Preserve privacy and avoid unnecessary cloud dependence.
8. Use mystical visual language as a design system, not as a replacement for understandable labels.
9. Never claim to detect spyware, malware, Pegasus, compromise, or intrusion unless the evidence truly supports that claim.
10. Never perform destructive actions automatically from heuristic results.

The defining experience should be:

> CoreGuard notices meaningful changes, explains its reasoning, admits uncertainty, and gives the user one calm next action.

---

# 2. Non-Negotiable Product Principles

## 2.1 Truth before drama

Every result must identify its evidence class:

- `OBSERVED`: Directly read from an Android API, package metadata, local file, cryptographic verification, or trusted operating-system source.
- `INFERRED`: Calculated from multiple observations or behavior patterns.
- `SIMULATED`: Educational, demonstration, laboratory, or fictional data.
- `UNAVAILABLE`: Android does not expose the required information to this application.
- `USER_REPORTED`: Entered or confirmed by the user.

No screen may display a simulated or inferred result as a confirmed observation.

## 2.2 Calm severity language

Use these user-facing severity levels:

- **Protected**
- **Informational**
- **Review Suggested**
- **Elevated Concern**
- **High Confidence Risk**

Avoid language such as:

- “You are hacked”
- “Pegasus detected”
- “Spyware confirmed”
- “Your phone is being watched”
- “Immediate compromise”

unless CoreGuard possesses direct, verifiable evidence that can support the exact statement.

## 2.3 No destructive automation

CoreGuard must not automatically:

- Factory reset the device
- Delete user files
- Wipe app data
- Disable unrelated applications
- Revoke permissions without explicit user interaction
- Upload private evidence
- Block emergency communications
- Trigger airplane mode
- Lock the user out of the device

CoreGuard may guide the user to appropriate Android settings and require confirmation before any sensitive action.

## 2.4 Privacy by default

Prefer:

- On-device processing
- Local encrypted storage
- Minimal retention
- Explicit export
- No hidden telemetry
- No collection of message contents
- No collection of passwords
- No collection of precise location unless an explicit feature requires it and the user has opted in

## 2.5 Battery-conscious operation

All continuous or periodic work must:

- Use lifecycle-aware collection
- Avoid tight polling
- Use WorkManager where appropriate
- Respect Doze mode
- Batch non-urgent checks
- Expose scan frequency settings
- Have measurable battery-cost targets
- Stop gracefully when permissions are unavailable

---

# 3. Current Foundation to Preserve

Do not remove or misrepresent existing capabilities.

Preserve and integrate:

- Native Kotlin Android structure
- Jetpack Compose UI
- Real RAM readings
- Existing local security heuristics
- Native C++ TamperGuard hot path
- Security swarm and CI concepts
- Google Play Billing
- Network Defense Lab as explicitly educational
- Existing release signing, checksum, and artifact-attestation approach
- Existing Quilla work
- Existing accessibility practices
- Existing honesty labels for simulated behavior

Before implementing each phase, Cursor must inspect the repository and adapt names and paths to the current codebase rather than blindly creating duplicates.

---

# 4. Target Architecture

Use a layered structure.

```text
UI / Compose
    ↓
Presentation / ViewModels
    ↓
Application Use Cases
    ↓
Domain Models and Policies
    ↓
Repositories
    ↓
Android Data Sources / Native TamperGuard / Local Database
```

Recommended package structure:

```text
com.coldboar.coreguard
├── app
├── core
│   ├── common
│   ├── model
│   ├── security
│   ├── storage
│   ├── design
│   └── testing
├── domain
│   ├── guardian
│   ├── findings
│   ├── timeline
│   ├── correlation
│   ├── hardening
│   ├── response
│   └── verification
├── data
│   ├── android
│   ├── local
│   ├── nativebridge
│   └── repositories
├── feature
│   ├── home
│   ├── findings
│   ├── timeline
│   ├── hardening
│   ├── response
│   ├── report
│   ├── verification
│   └── settings
└── quilla
```

Do not reorganize the entire project in one risky commit. Migrate incrementally.

---

# 5. Shared Domain Model

Implement or adapt the following core models.

```kotlin
enum class EvidenceClass {
    OBSERVED,
    INFERRED,
    SIMULATED,
    UNAVAILABLE,
    USER_REPORTED
}

enum class Severity {
    PROTECTED,
    INFORMATIONAL,
    REVIEW_SUGGESTED,
    ELEVATED_CONCERN,
    HIGH_CONFIDENCE_RISK
}

enum class Confidence {
    LOW,
    MEDIUM,
    HIGH,
    VERIFIED
}

enum class GuardianState {
    PROTECTED,
    OBSERVING,
    ATTENTION_REQUIRED,
    HIGH_RISK,
    SCANNING
}

enum class FindingCategory {
    DEVICE_INTEGRITY,
    APP_PERMISSION,
    ACCESSIBILITY,
    DEVICE_ADMIN,
    PACKAGE_CHANGE,
    NETWORK_CONFIGURATION,
    CERTIFICATE,
    DEBUGGING,
    ROOT_INDICATOR,
    SIGNATURE,
    OPERATING_SYSTEM,
    PRIVACY,
    SIMULATION
}

data class Evidence(
    val id: String,
    val evidenceClass: EvidenceClass,
    val source: String,
    val summary: String,
    val technicalDetail: String? = null,
    val collectedAtEpochMillis: Long,
    val verifiableValue: String? = null
)

data class RecommendedAction(
    val id: String,
    val label: String,
    val explanation: String,
    val actionType: ActionType,
    val destination: String? = null,
    val destructive: Boolean = false,
    val requiresConfirmation: Boolean = true
)

enum class ActionType {
    OPEN_ANDROID_SETTINGS,
    OPEN_APP_DETAILS,
    RUN_SCAN,
    REVIEW_EVIDENCE,
    EXPORT_REPORT,
    VERIFY_INSTALLATION,
    READ_GUIDANCE,
    DISMISS,
    NONE
}

data class SecurityFinding(
    val id: String,
    val category: FindingCategory,
    val severity: Severity,
    val confidence: Confidence,
    val title: String,
    val plainLanguageSummary: String,
    val whyItMatters: String,
    val possibleBenignCauses: List<String>,
    val evidence: List<Evidence>,
    val recommendedActions: List<RecommendedAction>,
    val firstSeenEpochMillis: Long,
    val lastSeenEpochMillis: Long,
    val active: Boolean,
    val detectorVersion: String
)
```

Rules:

- Every finding requires at least one `Evidence` item.
- Every inference must point to the observations used to produce it.
- Every elevated or high-risk finding requires at least one recommended action.
- Simulated findings must be visually and programmatically marked.
- Confidence and severity are separate concepts.
- A severe condition can still have low confidence.
- A high-confidence result can still be informational.

---

# 6. Feature One: Oracle Engine

## 6.1 Purpose

Turn raw detector output into calm, understandable security explanations.

## 6.2 Responsibilities

The Oracle Engine must produce:

- What CoreGuard noticed
- Evidence classification
- Confidence level
- Why it matters
- Possible harmless explanations
- One primary recommended action
- Optional secondary actions
- Timestamp
- Detector version

## 6.3 Interface

```kotlin
interface OracleEngine {
    suspend fun explain(
        signal: RawSecuritySignal,
        context: DeviceSecurityContext
    ): SecurityFinding
}
```

## 6.4 Rules engine

Start deterministic.

Do not use a generative model to invent security explanations.

Use:

- Typed rules
- Localized explanation templates
- Versioned mappings
- Unit-tested severity policies
- Explicit confidence calculation

Example:

```kotlin
data class ExplanationRule(
    val signalType: SignalType,
    val severity: Severity,
    val confidence: Confidence,
    val titleRes: Int,
    val summaryRes: Int,
    val whyItMattersRes: Int,
    val benignCauseRes: List<Int>,
    val actionFactory: (RawSecuritySignal) -> List<RecommendedAction>
)
```

## 6.5 Acceptance criteria

- No raw detector code directly constructs user-facing warning text.
- Every active detector has an explanation rule.
- Every explanation identifies evidence class.
- Every explanation includes a benign-cause section or explicitly says none are known.
- Unit tests cover all severity mappings.
- Screenshot tests cover protected, ambiguous, elevated, unavailable, and simulated states.
- No claim exceeds the strength of the evidence.

---

# 7. Feature Two: Truth Seals

## 7.1 Purpose

Make the origin and certainty of each result visible.

## 7.2 UI component

Create a reusable Compose component:

```kotlin
@Composable
fun TruthSeal(
    evidenceClass: EvidenceClass,
    confidence: Confidence?,
    modifier: Modifier = Modifier
)
```

User-facing labels:

- Observed
- Inferred
- Simulation
- Unavailable
- User reported

The component must include:

- Text label
- Accessible content description
- Optional icon
- Explanation tooltip or dialog
- Non-color-only distinction

## 7.3 Visual behavior

- `OBSERVED`: solid outline
- `INFERRED`: dotted or layered outline
- `SIMULATED`: clearly labeled educational treatment
- `UNAVAILABLE`: muted treatment
- `USER_REPORTED`: person or note indicator

Do not rely on mystical glyphs alone.

## 7.4 Acceptance criteria

- Every finding card displays a Truth Seal.
- Every exported report includes evidence class in text.
- Every simulated lab screen has a persistent “Simulation” marker.
- TalkBack reads the evidence class clearly.
- Color-blind users can distinguish all classes.

---

# 8. Feature Three: Guardian Pulse

## 8.1 Purpose

Provide one central, understandable representation of device-security posture.

## 8.2 State calculation

```kotlin
interface GuardianStateResolver {
    fun resolve(
        findings: List<SecurityFinding>,
        scanState: ScanState,
        dataAvailability: DataAvailability
    ): GuardianState
}
```

Suggested policy:

- `SCANNING` while an active user-requested scan runs.
- `HIGH_RISK` when at least one active, high-confidence, high-risk finding exists.
- `ATTENTION_REQUIRED` when elevated findings or multiple correlated review findings exist.
- `OBSERVING` when monitoring is healthy but data is incomplete or recent changes are being evaluated.
- `PROTECTED` when no meaningful active concerns exist and required checks succeeded.

Never map “no permission” to “protected.”

## 8.3 Animation

Animations must:

- Reflect real state
- Respect reduced-motion settings
- Pause when the screen is not visible
- Avoid continuous heavy GPU use
- Use subtle transitions
- Never flash rapidly

Suggested visual language:

- Protected: slow teal breathing
- Observing: soft cyan orbit
- Attention required: controlled gold ripple
- High risk: stable fractured-ring treatment, not frantic flashing
- Scanning: cyan sweep

## 8.4 Acceptance criteria

- The state resolver has deterministic unit tests.
- Reduced-motion mode uses static equivalents.
- Animation stops off-screen.
- State changes are explained beneath the visual.
- The user can tap the pulse to see the findings that caused the state.

---

# 9. Feature Four: Book of Changes

## 9.1 Purpose

Record meaningful security changes over time.

## 9.2 Event model

```kotlin
data class SecurityEvent(
    val id: String,
    val occurredAtEpochMillis: Long,
    val detectedAtEpochMillis: Long,
    val category: FindingCategory,
    val severity: Severity,
    val evidenceClass: EvidenceClass,
    val title: String,
    val explanation: String,
    val relatedPackageName: String? = null,
    val evidenceIds: List<String>,
    val sourceDetector: String,
    val eventHash: String,
    val previousEventHash: String?
)
```

## 9.3 Events to support

Where Android permissions and APIs permit:

- App installed
- App removed
- App updated
- Dangerous permission change
- Accessibility service enabled or disabled
- Device administrator change
- Unknown-app installation permission change
- Debugging configuration change
- VPN configuration change
- Private DNS change
- Security patch-level change
- Root or tamper indicator change
- CoreGuard signature verification change
- CoreGuard scan completion
- Finding opened or resolved
- User-confirmed event

Do not claim visibility where Android does not provide reliable access.

## 9.4 Storage

Use Room.

Suggested entities:

- `SecurityEventEntity`
- `EvidenceEntity`
- `FindingEntity`
- `FindingEvidenceCrossRef`
- `BaselineEntity`
- `CorrelationEntity`

Protect sensitive fields.

Consider:

- SQLCipher only if licensing, maintenance, and performance implications are accepted.
- Otherwise use Android Keystore-backed encryption for sensitive payloads.
- Do not create custom cryptography.

## 9.5 Tamper-evident chain

Optional but recommended:

```text
eventHash = SHA-256(
    canonicalEventPayload +
    previousEventHash
)
```

This is tamper evidence, not tamper prevention.

Document that distinction.

## 9.6 Retention

Default retention:

- 90 days for detailed events
- User-configurable 30, 90, 180 days
- Manual “keep indefinitely”
- Clear-all action with confirmation
- Export before deletion option

## 9.7 Acceptance criteria

- Events appear in reverse chronological order.
- Filtering works by severity, category, app, and evidence class.
- Timeline survives process death.
- Hash-chain validation identifies breaks.
- Deleting history is explicit and testable.
- No event is written repeatedly without a meaningful state transition.

---

# 10. Feature Five: Evidence Constellation

## 10.1 Purpose

Correlate multiple weak findings into a transparent narrative.

## 10.2 Important restriction

Correlation must not manufacture evidence.

The system may say:

> Several related changes occurred close together.

It may not say:

> Malware performed these actions.

unless direct evidence supports that statement.

## 10.3 Correlation model

```kotlin
data class CorrelationRule(
    val id: String,
    val requiredCategories: Set<FindingCategory>,
    val optionalCategories: Set<FindingCategory>,
    val timeWindowMillis: Long,
    val minimumDistinctSignals: Int,
    val resultingSeverity: Severity,
    val maximumConfidence: Confidence,
    val explanationTemplate: String
)

data class CorrelatedFinding(
    val id: String,
    val ruleId: String,
    val memberFindingIds: List<String>,
    val firstSignalAtEpochMillis: Long,
    val lastSignalAtEpochMillis: Long,
    val severity: Severity,
    val confidence: Confidence,
    val narrative: String
)
```

## 10.4 Initial rules

Implement conservative rules only.

### Rule A: Privilege escalation pattern

Signals:

- New or recently updated app
- Accessibility service enabled
- Overlay permission enabled
- Battery optimization exemption
- Device administrator activated

Result:

- Severity: Elevated Concern
- Maximum confidence: Medium
- Evidence class: Inferred

### Rule B: CoreGuard integrity pattern

Signals:

- Signature mismatch
- Debugger attached
- Native TamperGuard anomaly

Result:

- Severity: High Confidence Risk only when signature mismatch is verified and supporting evidence exists
- Otherwise Elevated Concern

### Rule C: Sideload-and-privilege pattern

Signals:

- App installed from unknown source
- Sensitive permission granted
- Background persistence capability enabled

Result:

- Severity: Review Suggested or Elevated Concern depending on evidence count
- Maximum confidence: Medium

## 10.5 Acceptance criteria

- Correlation rules are versioned.
- Each correlated result lists all source findings.
- The user can open every source finding.
- No rule produces `VERIFIED` confidence unless a verified input supports it.
- Tests cover event ordering, missing evidence, duplicate signals, expired time windows, and false-positive controls.

---

# 11. Feature Six: Quilla Private Baseline

## 11.1 Purpose

Learn normal device posture locally and detect meaningful deviations.

## 11.2 Initial baseline scope

Start with stable, low-risk metadata:

- Installed package set
- App version changes
- Permission snapshots
- Enabled accessibility services
- Enabled device administrators
- Security patch level
- CoreGuard integrity status
- User-approved trusted apps
- Historical finding frequency

Do not start by collecting raw traffic payloads, message contents, keystrokes, clipboard history, contact data, or precise location.

## 11.3 Baseline model

```kotlin
data class DeviceBaseline(
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val packageSnapshots: List<PackageBaseline>,
    val permissionSnapshots: List<PermissionBaseline>,
    val trustedPackages: Set<String>,
    val securityPatchLevel: String?,
    val accessibilityServices: Set<String>,
    val deviceAdminPackages: Set<String>
)
```

## 11.4 Learning policy

- First seven days: learning mode
- Show “Learning your normal security posture”
- Do not label deviations as threats during initial learning
- Allow the user to mark an app or configuration as trusted
- Recalculate gradually
- Preserve an audit trail of baseline changes
- Never silently mark a suspicious change as trusted

## 11.5 Risk scoring

Use an explainable score, not an opaque “AI score.”

Example:

```text
score =
    noveltyWeight
  + privilegeWeight
  + persistenceWeight
  + integrityWeight
  + correlationWeight
  - trustedContextReduction
```

Every score contribution must be visible in developer diagnostics and testable.

## 11.6 Acceptance criteria

- Baseline data remains local by default.
- The baseline can be reset.
- The user can review trusted exceptions.
- Quilla explains why a deviation was flagged.
- No black-box score is shown without supporting factors.
- Learning mode cannot produce high-confidence compromise claims.

---

# 12. Feature Seven: Ward Circle Hardening Journey

## 12.1 Purpose

Turn device hardening into a guided, achievable process.

## 12.2 Checks

Where reliably available:

- Screen-lock presence
- Biometric availability
- Developer options
- USB debugging
- Unknown-app installation access
- Enabled accessibility services
- Enabled device administrators
- Notification access
- VPN status
- Private DNS status
- Security patch age
- Play Protect guidance
- Lock-screen notification privacy
- Backup configuration guidance
- CoreGuard notification permission
- Battery optimization status for CoreGuard

## 12.3 Check model

```kotlin
data class HardeningCheck(
    val id: String,
    val title: String,
    val description: String,
    val status: HardeningStatus,
    val evidenceClass: EvidenceClass,
    val importance: Severity,
    val action: RecommendedAction?,
    val lastCheckedEpochMillis: Long
)

enum class HardeningStatus {
    PASSED,
    REVIEW,
    FAILED,
    UNAVAILABLE,
    MANUAL_CONFIRMATION_REQUIRED
}
```

## 12.4 UI

Display:

- Overall completion percentage
- Segmented protective ring
- Plain-language list
- “Fix next” primary action
- Manual checks clearly marked
- No false sense of complete protection

Use wording:

> Security hardening progress

not:

> Your phone is unhackable

## 12.5 Acceptance criteria

- Every check explains how it was measured.
- Manual checks are not marked observed.
- Settings deep links fail gracefully.
- Completion status never implies immunity.
- Accessibility and reduced-motion support are complete.

---

# 13. Feature Eight: Ritual of Response

## 13.1 Purpose

Provide a calm guided response to serious concerns.

## 13.2 Response flow

1. Review evidence
2. Preserve or export evidence
3. Identify recently changed apps or settings
4. Open relevant Android settings
5. Remove suspicious privileges through user-controlled Android UI
6. Recommend changing important passwords from a trusted device when justified
7. Recommend OS update or reset only when appropriate
8. Document resolution

## 13.3 Response plan model

```kotlin
data class ResponsePlan(
    val findingId: String,
    val title: String,
    val summary: String,
    val steps: List<ResponseStep>,
    val createdAtEpochMillis: Long
)

data class ResponseStep(
    val id: String,
    val order: Int,
    val title: String,
    val explanation: String,
    val action: RecommendedAction?,
    val completed: Boolean,
    val requiresExternalTrustedDevice: Boolean = false
)
```

## 13.4 Emergency screen actions

Safe actions may include:

- Review evidence
- Export report
- Open network settings
- Open app details
- Open accessibility settings
- Open device administrator settings
- Open unknown-app settings
- Read trusted recovery guidance
- Call a trusted contact through normal Android intent
- Mark a step complete

Do not add fake one-tap controls that Android does not permit.

## 13.5 Acceptance criteria

- No destructive step runs automatically.
- Sensitive actions require confirmation.
- Every step explains why it is recommended.
- The user can leave and resume the plan.
- Export can occur before local deletion.
- Response content distinguishes suspected, inferred, and verified conditions.

---

# 14. Feature Nine: Verify CoreGuard

## 14.1 Purpose

Expose supply-chain integrity to the user.

## 14.2 Capabilities

Show:

- Installed version name and code
- Package name
- Signing certificate fingerprint
- Expected official signing fingerprint
- Installation source, when available
- Build type
- Integrity result
- Link or instructions for official release verification
- Release checksum guidance
- Artifact attestation guidance

## 14.3 Verification result

```kotlin
data class InstallationVerification(
    val packageNameMatches: Boolean,
    val signatureMatches: Boolean,
    val expectedCertificateSha256: String?,
    val installedCertificateSha256: String?,
    val installerPackage: String?,
    val buildType: String,
    val verifiedAtEpochMillis: Long,
    val evidence: List<Evidence>
)
```

## 14.4 Rules

- Store expected signing identity safely and document update procedures.
- Never hardcode private signing material.
- A signature mismatch must be presented clearly.
- Debug builds must be labeled.
- Forks must not be falsely labeled malicious merely because they use a different signature.
- The app should say “This installation does not match the official CoreGuard signing identity,” not “This is malware.”

## 14.5 Acceptance criteria

- Verification works offline.
- Debug and release behavior are tested.
- Certificate formatting is normalized.
- The screen explains forks and unofficial builds.
- All values can be copied for manual verification.

---

# 15. Feature Ten: Silent Sigil Design System

## 15.1 Purpose

Create a unique mystical identity while preserving professional security UX.

## 15.2 Design rules

Use:

- Deep charcoal or near-black surfaces
- Teal as protected-state color
- Cyan for active analysis
- Gold for meaningful milestones and primary attention
- Amber for uncertainty
- Red only for strong, high-confidence risk
- Low-opacity geometric sigils
- Subtle linework
- Spacious layouts
- Strong typography hierarchy
- High contrast
- Shape and text in addition to color

## 15.3 Typography

Recommended:

- Cinzel or another ceremonial serif for:
  - Logo
  - Major section title
  - Limited branding moments
- Readable sans-serif for:
  - Findings
  - Evidence
  - Settings
  - Reports
  - Buttons
  - Explanations

Do not use decorative type for long paragraphs.

## 15.4 Glyph system

Assign each category a unique glyph, but always pair it with text.

Examples:

- Device Integrity
- Permissions
- Accessibility
- Network Configuration
- App Change
- Certificate
- Privacy
- Simulation

The glyph must never carry meaning alone.

## 15.5 Motion system

- Slow, deliberate movement
- No rapid flashing
- Reduced-motion alternative
- Pause off-screen
- Maximum animation durations documented
- Avoid decorative motion during urgent decision-making

## 15.6 Acceptance criteria

- WCAG-aware contrast checks pass.
- TalkBack labels all decorative and meaningful graphics properly.
- Decorative sigils are excluded from accessibility traversal.
- The design remains understandable in grayscale.
- No decorative animation causes excessive battery use.

---

# 16. Reporting

## 16.1 Report types

- Quick security summary
- Detailed evidence report
- Timeline export
- Installation verification report
- Hardening progress report
- Incident response record

## 16.2 Report requirements

Every report must include:

- CoreGuard version
- Report creation time
- Device model and Android version, with privacy controls
- Evidence-class legend
- Severity legend
- Findings
- Confidence
- Evidence
- Possible benign causes
- Recommended actions
- Clear disclaimer
- Simulation labels
- Hash or report identifier

## 16.3 Privacy controls

Before export, allow the user to exclude:

- Package names
- Device model
- Android ID or any identifier
- Timestamps
- User notes
- Network-related metadata

Never include secrets, tokens, message contents, passwords, or authentication material.

---

# 17. Data and Security Requirements

## 17.1 Secure storage

Use:

- Android Keystore
- Encrypted local preferences where appropriate
- Room for structured history
- Carefully scoped file exports
- ContentProvider or FileProvider for sharing

Avoid:

- Hardcoded keys
- Custom encryption algorithms
- Storing sensitive data in logs
- World-readable files
- Unprotected temporary report files

## 17.2 Logging

Production logs must not expose:

- Full package inventories unless necessary
- User identifiers
- Certificate data beyond what is needed
- Internal paths
- Sensitive scan evidence
- Export contents

Create a redaction utility.

## 17.3 Native bridge

For TamperGuard:

- Validate JNI inputs
- Handle native failures safely
- Avoid crashing the app on unsupported devices
- Version native evidence
- Treat native anomalies as evidence, not automatic proof of compromise
- Add unit or instrumentation coverage around bridge behavior

## 17.4 Threat model

Create:

`docs/THREAT_MODEL.md`

Include:

- Assets
- Trust boundaries
- Attacker capabilities
- Abuse cases
- Data flows
- Mitigations
- Residual risks
- Explicit non-goals

---

# 18. Play Store and Claim Safety

Maintain a claim inventory.

Create:

`docs/CLAIMS_MATRIX.md`

Columns:

- Feature
- User-facing claim
- Evidence source
- Evidence class
- Technical limitation
- Play Store risk
- Required disclaimer
- Owner

Examples:

| Feature | Safe claim |
|---|---|
| Root heuristics | “Checks for indicators commonly associated with modified devices.” |
| Signature check | “Verifies whether this installation matches the expected signing identity.” |
| Permission history | “Tracks permission-related changes visible to CoreGuard.” |
| Network Defense Lab | “Educational network-defense simulation.” |
| Quilla baseline | “Highlights changes from the device’s established local baseline.” |

Never market the app as guaranteed protection.

---

# 19. Testing Strategy

## 19.1 Unit tests

Cover:

- Severity policy
- Confidence calculation
- Truth Seal mapping
- Guardian state resolver
- Oracle templates
- Timeline deduplication
- Hash-chain validation
- Correlation windows
- Baseline scoring
- Trusted-app exceptions
- Report redaction
- Installation verification formatting

## 19.2 Instrumentation tests

Cover:

- Main navigation
- Finding details
- Truth Seal accessibility
- Timeline filtering
- Hardening deep links
- Response-plan persistence
- Reduced-motion behavior
- Report generation
- Process death and restore
- Permission-denied states

## 19.3 UI tests

Test at minimum:

- Protected state
- No-data state
- Permission unavailable
- One low-confidence finding
- Multiple correlated findings
- High-confidence integrity mismatch
- Simulation screen
- Long text
- Large font
- Dark theme
- TalkBack labels

## 19.4 Security tests

- Dependency scanning
- Secret scanning
- Static analysis
- Lint
- Native hardening checks
- Export path validation
- Intent validation
- FileProvider configuration
- Release-signing checks
- Reproducibility documentation
- Artifact attestation verification

---

# 20. Performance Targets

Set measurable targets.

- Home screen usable within 1 second after app process is ready.
- No main-thread disk or network access.
- No continuous polling faster than justified.
- Timeline query under 200 ms for 10,000 local events on a representative device.
- Guardian Pulse animation remains smooth without persistent high GPU load.
- Background work stays within documented battery budget.
- Scans expose progress and cancellation where possible.
- Database writes are batched where safe.
- Expensive package scans are cached and invalidated by package events.

---

# 21. Accessibility Requirements

Every new feature must support:

- TalkBack
- Dynamic type
- Minimum touch target size
- Non-color-only meaning
- Reduced motion
- Clear focus order
- Descriptive button labels
- Meaningful empty states
- Plain-language explanations
- High contrast
- Landscape where practical

Mystical symbols are decorative unless paired with a clear accessible label.

---

# 22. Recommended Implementation Phases

## Phase 0: Repository audit and safety baseline

Tasks:

1. Map current architecture.
2. Identify existing detector interfaces.
3. Identify current database and state management.
4. Identify existing Quilla classes.
5. Identify existing TamperGuard JNI bridge.
6. Identify current design tokens.
7. Identify simulated screens.
8. Run tests, lint, and build.
9. Create `docs/COREGUARD_GUARDIAN_BLUEPRINT.md`.
10. Create issues for each following phase.

Deliverable:

- No product behavior changes.
- Clear architecture map.
- Baseline test and build results.

## Phase 1: Shared truth model

Implement:

- EvidenceClass
- Severity
- Confidence
- Evidence
- SecurityFinding
- RecommendedAction
- Mapping adapters for existing detectors
- TruthSeal component

Do not redesign the full application yet.

## Phase 2: Oracle Engine

Implement deterministic explanations for all existing checks.

Deliver:

- Finding details screen
- Benign-cause section
- Primary next action
- Evidence list
- Confidence explanation

## Phase 3: Guardian Pulse

Implement:

- GuardianStateResolver
- Central pulse component
- Reduced-motion behavior
- Tap-through to causal findings

## Phase 4: Book of Changes

Implement:

- Room schema
- Event ingestion
- Deduplication
- Timeline UI
- Filtering
- Retention
- Hash-chain validation

## Phase 5: Evidence Constellation

Implement three conservative correlation rules.

Do not add machine learning yet.

## Phase 6: Ward Circle

Implement hardening checks and guided settings actions.

## Phase 7: Quilla Private Baseline

Implement learning mode, trusted exceptions, explainable deviation scoring, and reset controls.

## Phase 8: Ritual of Response

Implement resumable response plans and safe settings navigation.

## Phase 9: Verify CoreGuard

Implement local signing identity verification and official-build explanation.

## Phase 10: Reports and polish

Implement redacted reports, visual refinement, performance tuning, accessibility audit, and Play Store claim review.

---

# 23. Definition of Done for Every Phase

A phase is not complete until:

- Code builds successfully.
- Unit tests pass.
- Relevant instrumentation tests pass.
- Lint passes or documented pre-existing failures remain unchanged.
- No secrets are added.
- No unsupported claim is introduced.
- Accessibility is reviewed.
- Battery impact is considered.
- README or feature documentation is updated.
- Screens contain loading, empty, unavailable, and error states.
- The code uses current project patterns.
- New behavior is behind a feature flag when rollout risk is meaningful.
- Changelog entry is added.
- Screenshots are captured for visual changes.
- The PR description lists risks and rollback steps.

---

# 24. Cursor Execution Rules

Cursor must follow these rules while implementing.

1. Inspect before editing.
2. Do not create duplicate architecture layers if equivalents already exist.
3. Make small, reviewable commits.
4. Do not rewrite unrelated files.
5. Preserve existing release behavior.
6. Preserve accurate simulation labels.
7. Never silently weaken security checks.
8. Never suppress failing tests merely to make CI green.
9. Never replace real Android builds with placeholder artifacts.
10. Do not introduce a backend unless the feature truly requires one.
11. Prefer local deterministic logic.
12. Do not add an LLM to the on-device hot path.
13. Do not collect personal content.
14. Do not add destructive “panic wipe” behavior.
15. Do not claim live network monitoring unless implemented and permitted.
16. Add comments explaining security-sensitive decisions.
17. Record limitations in documentation.
18. Ask for no additional product decisions when a safe conservative default is available.
19. When uncertain, choose the least invasive behavior.
20. Keep every feature honest, explainable, reversible, and testable.

---

# 25. First Cursor Prompt

Copy the prompt below into Cursor after attaching this file.

```text
You are working in the CoreGuard-Android repository.

Read this entire blueprint before modifying code.

Begin with Phase 0 only.

Tasks:
1. Inspect the current repository structure and identify the exact existing classes, packages, Compose screens, detector interfaces, Room or persistence layers, Quilla components, TamperGuard JNI bridge, billing components, simulation features, and design-system files.
2. Run or inspect the expected build, lint, and test commands.
3. Create docs/COREGUARD_GUARDIAN_ARCHITECTURE_AUDIT.md containing:
   - Current architecture
   - Existing reusable components
   - Gaps against this blueprint
   - Exact proposed file paths for Phase 1
   - Migration risks
   - Test baseline
4. Do not implement Phase 1 yet.
5. Do not rename packages or perform broad refactors.
6. Do not weaken any security, CI, release, signing, or simulation-honesty behavior.
7. Finish with a concise summary of findings and a Phase 1 implementation checklist.

Use the repository’s existing conventions. Be precise about what is real, inferred, simulated, unavailable, and user-reported.
```

---

# 26. Phase 1 Cursor Prompt

Use only after Phase 0 is complete.

```text
Implement Phase 1 of the CoreGuard Guardian Intelligence Blueprint.

Goal:
Introduce a shared truth-and-evidence domain model and a reusable Truth Seal UI without changing the meaning of existing security checks.

Requirements:
1. Reuse existing package and architecture conventions.
2. Add or adapt:
   - EvidenceClass
   - Severity
   - Confidence
   - Evidence
   - SecurityFinding
   - RecommendedAction
   - ActionType
3. Add adapters that convert current detector results into the new model.
4. Add a reusable Compose TruthSeal component.
5. Add clear labels for Observed, Inferred, Simulation, Unavailable, and User reported.
6. Do not treat inferred or simulated data as observed.
7. Add unit tests for all mappings.
8. Add Compose tests for semantics and labels.
9. Preserve all existing behavior unless a change is required to expose accurate evidence metadata.
10. Update documentation and changelog.
11. Run the relevant tests, lint, and build.
12. Report every modified file and any remaining gaps.

Do not implement the Oracle Engine, timeline, correlation, baseline learning, or visual redesign in this phase.
```

---

# 27. Quality Bar

The finished CoreGuard should feel:

- Protective, not frightening
- Mystical, not confusing
- Intelligent, not deceptive
- Powerful, not destructive
- Premium, not cluttered
- Technical, but understandable
- Honest about Android limitations
- Useful even when no threat is found
- Calm during high-risk moments
- Worth trusting with sensitive security decisions

---

# 28. Final Product Promise

CoreGuard does not promise that nothing bad can happen.

CoreGuard promises to:

- Observe what Android allows it to observe
- Preserve the distinction between fact and inference
- Explain its reasoning
- Track meaningful changes
- Protect user privacy
- Help users make informed security decisions
- Never hide uncertainty behind dramatic language

package com.coldboar.coreguard.truth

import com.coldboar.coreguard.EvidenceKind
import com.coldboar.coreguard.GuardianScoreEvidence
import com.coldboar.coreguard.SecurityCheckState

/**
 * How evidence for a finding was produced.
 *
 * Truth-first rule: NEVER promote INFERRED or SIMULATED evidence to OBSERVED.
 * Absence of evidence is UNAVAILABLE, not safe / OBSERVED.
 */
enum class EvidenceClass {
    /** Directly measured or cryptographically attested — on-device observation. */
    OBSERVED,
    /** Derived or inferred from indirect signals; not directly measured. */
    INFERRED,
    /** Produced by an emulated or synthetic scenario; not from a live device. */
    SIMULATED,
    /** The data required to make a determination is not accessible on this device. */
    UNAVAILABLE,
    /** Reported by the user; unverified by the engine. */
    USER_REPORTED
}

/** Severity of a finding — how serious the condition is if confirmed. */
enum class FindingSeverity {
    INFORMATIONAL,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

/** How confident the engine is that the finding reflects reality. */
enum class ConfidenceLevel {
    /** Weak signal; many false positives possible. */
    LOW,
    /** Moderate corroboration; some uncertainty remains. */
    MODERATE,
    /** Strong corroboration from multiple independent signals. */
    HIGH,
    /** Cryptographically or externally attested; as certain as possible on-device. */
    VERIFIED
}

/**
 * A single normalized, explainable security finding.
 *
 * All fields are required so that the explanation formatter and UI can always
 * render a complete, honest representation. Empty strings are acceptable
 * placeholders when a field is genuinely not applicable.
 */
data class Finding(
    /** Stable unique identifier (e.g. "nemesis.package.com.spyware"). */
    val id: String,
    /** Short human-readable title. */
    val title: String,
    /** One-sentence summary for non-technical users. */
    val plainSummary: String,
    /** Detailed technical description for analyst mode. */
    val technicalDescription: String,
    /** How the evidence was obtained. */
    val evidenceClass: EvidenceClass,
    /** How serious the condition is if the finding is correct. */
    val severity: FindingSeverity,
    /** How confident the engine is that the finding is accurate. */
    val confidence: ConfidenceLevel,
    /** Which subsystem or module produced this finding (e.g. "NemesisScanner", "GuardianScore"). */
    val source: String,
    /** When the finding was produced (epoch milliseconds). */
    val timestampMs: Long,
    /** Which part of the device this finding relates to. */
    val affectedComponent: String,
    /** Values actually observed on the device (e.g. installed package names). */
    val observedValues: List<String>,
    /** Expected or known-safe baseline values for comparison. */
    val baselineValues: List<String>,
    /** Why this finding matters to the user in plain language. */
    val whyItMatters: String,
    /** Recommended action the user can take. */
    val recommendedResponse: String,
    /** How this finding could be independently verified. */
    val verificationMethod: String,
    /** Current state of independent verification (e.g. "Unverified", "Confirmed by MVT"). */
    val verificationStatus: String,
    /** External threat-intel references (e.g. CVE IDs, IOC hashes, Amnesty report URLs). */
    val threatIntelReferences: List<String>,
    /** Known Android OS visibility limits that affect this finding's completeness. */
    val androidVisibilityLimits: String
)

// ---------------------------------------------------------------------------
// Mapper: EvidenceKind → EvidenceClass / ConfidenceLevel
// ---------------------------------------------------------------------------

/**
 * Maps the legacy [EvidenceKind] enum to the shared [EvidenceClass].
 *
 * Mapping logic:
 * - VERIFIED → OBSERVED (cryptographic / attestation signal)
 * - HEURISTIC → INFERRED (best-effort heuristic, not directly measured)
 * - EDUCATIONAL → UNAVAILABLE (policy guidance; no live detection performed)
 */
fun EvidenceKind.toEvidenceClass(): EvidenceClass = when (this) {
    EvidenceKind.VERIFIED -> EvidenceClass.OBSERVED
    EvidenceKind.HEURISTIC -> EvidenceClass.INFERRED
    EvidenceKind.EDUCATIONAL -> EvidenceClass.UNAVAILABLE
}

/**
 * Maps the legacy [EvidenceKind] enum to a [ConfidenceLevel].
 *
 * - VERIFIED → VERIFIED (cryptographic certainty)
 * - HEURISTIC → MODERATE (heuristics have meaningful but imperfect accuracy)
 * - EDUCATIONAL → LOW (no live measurement performed)
 */
fun EvidenceKind.toConfidenceLevel(): ConfidenceLevel = when (this) {
    EvidenceKind.VERIFIED -> ConfidenceLevel.VERIFIED
    EvidenceKind.HEURISTIC -> ConfidenceLevel.MODERATE
    EvidenceKind.EDUCATIONAL -> ConfidenceLevel.LOW
}

// ---------------------------------------------------------------------------
// Mapper: SecurityCheckState → FindingSeverity
// ---------------------------------------------------------------------------

/**
 * Maps a [SecurityCheckState] to [FindingSeverity].
 *
 * - PASS → INFORMATIONAL (nothing concerning)
 * - WARN → MEDIUM (notable but not confirmed high-risk)
 * - FAIL → HIGH (high-risk indicator present)
 */
fun SecurityCheckState.toFindingSeverity(): FindingSeverity = when (this) {
    SecurityCheckState.PASS -> FindingSeverity.INFORMATIONAL
    SecurityCheckState.WARN -> FindingSeverity.MEDIUM
    SecurityCheckState.FAIL -> FindingSeverity.HIGH
}

// ---------------------------------------------------------------------------
// Conversion: GuardianScoreEvidence → Finding
// ---------------------------------------------------------------------------

/**
 * Converts a [GuardianScoreEvidence] row to a normalized [Finding].
 *
 * The [GuardianScoreEvidence.state] field maps to both [FindingSeverity] and
 * is reflected in [Finding.observedValues] for full transparency.
 */
fun GuardianScoreEvidence.toFinding(): Finding = Finding(
    id = "guardian.${checkId}",
    title = displayName,
    plainSummary = explanation,
    technicalDescription = explanation,
    evidenceClass = confidence.toEvidenceClass(),
    severity = state.toFindingSeverity(),
    confidence = confidence.toConfidenceLevel(),
    source = "GuardianScore",
    timestampMs = timestampMs,
    affectedComponent = displayName,
    observedValues = listOf("state=${state.name}"),
    baselineValues = listOf("state=${SecurityCheckState.PASS.name}"),
    whyItMatters = explanation,
    recommendedResponse = recommendedAction,
    verificationMethod = "Re-run Guardian Score check",
    verificationStatus = "Unverified — heuristic result",
    threatIntelReferences = emptyList(),
    androidVisibilityLimits = "Visibility depends on device configuration and Android API level."
)

// ---------------------------------------------------------------------------
// Deterministic explanation formatter
// ---------------------------------------------------------------------------

/**
 * Formats a [Finding] into a deterministic, structured plain-text explanation
 * with five fixed sections:
 *
 * 1. Conclusion — what the finding means
 * 2. Evidence — what was observed and how it was obtained
 * 3. Confidence — how reliable the assessment is
 * 4. Recommended action — what the user should do
 * 5. What could change the conclusion — conditions that would alter the verdict
 *
 * This function is pure (no side effects, deterministic output) and is the
 * single source of truth for human-readable finding explanations across the app.
 */
fun formatFindingExplanation(finding: Finding): String {
    val severityLabel = when (finding.severity) {
        FindingSeverity.INFORMATIONAL -> "informational"
        FindingSeverity.LOW -> "low severity"
        FindingSeverity.MEDIUM -> "medium severity"
        FindingSeverity.HIGH -> "high severity"
        FindingSeverity.CRITICAL -> "critical severity"
    }
    val evidenceLabel = when (finding.evidenceClass) {
        EvidenceClass.OBSERVED -> "directly observed on this device"
        EvidenceClass.INFERRED -> "inferred from indirect signals"
        EvidenceClass.SIMULATED -> "based on a simulated or synthetic scenario"
        EvidenceClass.UNAVAILABLE -> "unavailable — the required data cannot be accessed on this device"
        EvidenceClass.USER_REPORTED -> "reported by the user; not independently verified"
    }
    val confidenceLabel = when (finding.confidence) {
        ConfidenceLevel.LOW -> "low — weak signal; many false positives possible"
        ConfidenceLevel.MODERATE -> "moderate — some uncertainty remains"
        ConfidenceLevel.HIGH -> "high — strong corroboration from multiple signals"
        ConfidenceLevel.VERIFIED -> "verified — cryptographically or externally attested"
    }
    val observedSummary = if (finding.observedValues.isEmpty()) {
        "No specific values observed."
    } else {
        finding.observedValues.joinToString("; ")
    }

    return buildString {
        appendLine("=== Conclusion ===")
        appendLine("${finding.title}: ${finding.plainSummary}")
        appendLine("Severity: $severityLabel.")
        if (finding.androidVisibilityLimits.isNotBlank()) {
            appendLine("Note: ${finding.androidVisibilityLimits}")
        }
        appendLine()
        appendLine("=== Evidence ===")
        appendLine("Evidence class: ${finding.evidenceClass.name} ($evidenceLabel).")
        appendLine("Source: ${finding.source}.")
        appendLine("Observed: $observedSummary")
        if (finding.technicalDescription.isNotBlank() &&
            finding.technicalDescription != finding.plainSummary
        ) {
            appendLine("Technical: ${finding.technicalDescription}")
        }
        appendLine()
        appendLine("=== Confidence ===")
        appendLine("Confidence: $confidenceLabel.")
        appendLine("Verification status: ${finding.verificationStatus}.")
        if (finding.verificationMethod.isNotBlank()) {
            appendLine("Verification method: ${finding.verificationMethod}.")
        }
        appendLine()
        appendLine("=== Recommended action ===")
        appendLine(finding.recommendedResponse.ifBlank { "No specific action required at this time." })
        appendLine()
        appendLine("=== What could change the conclusion ===")
        appendLine(
            if (finding.whyItMatters.isNotBlank()) finding.whyItMatters
            else "Additional data or a full forensic acquisition could alter this assessment."
        )
        if (finding.threatIntelReferences.isNotEmpty()) {
            appendLine()
            appendLine("References: ${finding.threatIntelReferences.joinToString(", ")}")
        }
    }.trimEnd()
}

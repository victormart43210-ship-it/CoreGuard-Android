package com.coldboar.coreguard.truth

import com.coldboar.coreguard.EvidenceKind
import com.coldboar.coreguard.GuardianScoreEvidence
import com.coldboar.coreguard.SecurityCheckState

/**
 * Phase 1 — Shared Truth Architecture
 *
 * Canonical evidence classification for every finding emitted by any CoreGuard
 * engine. Modelled after the principle: "what the OS let us observe, not what
 * we'd like to claim".
 */

/** How the finding's evidence was obtained. */
enum class EvidenceClass {
    /** Directly observed via OS API, system file, or verifiable attestation. */
    OBSERVED,

    /** Derived from heuristics or behavioural patterns — not directly seen. */
    INFERRED,

    /** Produced by a local simulation, lab fixture, or test dataset. */
    SIMULATED,

    /** Data was requested but the OS returned no value or access was denied. */
    UNAVAILABLE,

    /** Explicitly supplied by the user (e.g. manual report / self-assessment). */
    USER_REPORTED
}

/** How significant this finding is if real. */
enum class FindingSeverity {
    INFORMATIONAL,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

/** How confident we are that the evidence is accurate and complete. */
enum class ConfidenceLevel {
    /** Heuristic with known gaps; treat as signal, not proof. */
    LOW,

    /** Multiple supporting signals but not independently verifiable. */
    MODERATE,

    /** Strong, reproducible evidence from a reliable OS source. */
    HIGH,

    /** Cryptographically attested or independently confirmed. */
    VERIFIED
}

/**
 * A single, self-contained security finding.
 *
 * All fields are required; use empty strings / empty lists rather than null so
 * the formatter and UI never need null checks.
 */
data class Finding(
    /** Stable, unique ID (e.g. "nemesis:pkg:com.evil.app" or "guardian:root_check"). */
    val id: String,

    /** Short, human-readable title. */
    val title: String,

    /** One-sentence plain-language summary for non-technical users. */
    val plainSummary: String,

    /** Technical detail for analysts / Evidence Mode. */
    val technicalDescription: String,

    /** How the evidence was obtained. */
    val evidenceClass: EvidenceClass,

    /** Significance level. */
    val severity: FindingSeverity,

    /** Confidence in the evidence. */
    val confidence: ConfidenceLevel,

    /** Which engine or subsystem produced this finding. */
    val source: String,

    /** When this finding was produced (epoch millis). */
    val timestampMs: Long,

    /** Human-readable name of the affected component. */
    val affectedComponent: String,

    /** Raw values actually observed on the device. */
    val observedValues: List<String>,

    /** Expected / safe baseline values for comparison. */
    val baselineValues: List<String>,

    /** Plain-language explanation of why this matters to the user. */
    val whyItMatters: String,

    /** Concrete recommended next step for the user. */
    val recommendedResponse: String,

    /** How to independently verify this finding. */
    val verificationMethod: String,

    /** Current verification status (e.g. "Not yet independently verified"). */
    val verificationStatus: String,

    /** Threat-intel references (CVEs, IOC sources, researcher URLs). */
    val threatIntelReferences: List<String>,

    /** Android-specific visibility limits that constrain this evidence. */
    val androidVisibilityLimits: String
)

// ---------------------------------------------------------------------------
// Mappers: EvidenceKind → EvidenceClass / ConfidenceLevel
// ---------------------------------------------------------------------------

/** Maps the legacy [EvidenceKind] to the canonical [EvidenceClass]. */
fun EvidenceKind.toEvidenceClass(): EvidenceClass = when (this) {
    EvidenceKind.VERIFIED -> EvidenceClass.OBSERVED
    EvidenceKind.HEURISTIC -> EvidenceClass.INFERRED
    EvidenceKind.EDUCATIONAL -> EvidenceClass.INFERRED
}

/** Maps [EvidenceKind] to a [ConfidenceLevel]. */
fun EvidenceKind.toConfidenceLevel(): ConfidenceLevel = when (this) {
    EvidenceKind.VERIFIED -> ConfidenceLevel.VERIFIED
    EvidenceKind.HEURISTIC -> ConfidenceLevel.MODERATE
    EvidenceKind.EDUCATIONAL -> ConfidenceLevel.LOW
}

// ---------------------------------------------------------------------------
// Mapper: GuardianScoreEvidence → Finding
// ---------------------------------------------------------------------------

/**
 * Converts a [GuardianScoreEvidence] row into a canonical [Finding].
 *
 * [GuardianScoreEvidence] carries a single check result; the resulting
 * [Finding] severity is derived from the check state.
 */
fun GuardianScoreEvidence.toFinding(): Finding {
    val severity = when (this.state) {
        SecurityCheckState.PASS -> FindingSeverity.INFORMATIONAL
        SecurityCheckState.WARN -> FindingSeverity.MEDIUM
        SecurityCheckState.FAIL -> FindingSeverity.HIGH
    }
    return Finding(
        id = "guardian:${this.checkId}",
        title = this.displayName,
        plainSummary = this.explanation,
        technicalDescription = this.explanation,
        evidenceClass = this.confidence.toEvidenceClass(),
        severity = severity,
        confidence = this.confidence.toConfidenceLevel(),
        source = "GuardianScore",
        timestampMs = this.timestampMs,
        affectedComponent = this.displayName,
        observedValues = emptyList(),
        baselineValues = emptyList(),
        whyItMatters = this.explanation,
        recommendedResponse = this.recommendedAction,
        verificationMethod = "Re-run Guardian Score check",
        verificationStatus = "Not independently verified",
        threatIntelReferences = emptyList(),
        androidVisibilityLimits = "Result is heuristic; OS API visibility limits apply."
    )
}

// ---------------------------------------------------------------------------
// Deterministic explanation formatter
// ---------------------------------------------------------------------------

/**
 * Formats a [Finding] into a structured plain-text explanation with a fixed
 * five-section structure:
 *
 * 1. Conclusion
 * 2. Evidence
 * 3. Confidence
 * 4. Recommended action
 * 5. What could change the conclusion
 *
 * Pure function — no I/O, no state, deterministic output for given input.
 */
fun formatFindingExplanation(finding: Finding): String = buildString {
    appendLine("=== Conclusion ===")
    appendLine(finding.plainSummary)
    appendLine()

    appendLine("=== Evidence ===")
    appendLine("Evidence class : ${finding.evidenceClass.name}")
    appendLine("Source         : ${finding.source}")
    if (finding.observedValues.isNotEmpty()) {
        appendLine("Observed       : ${finding.observedValues.joinToString(", ")}")
    }
    if (finding.baselineValues.isNotEmpty()) {
        appendLine("Baseline       : ${finding.baselineValues.joinToString(", ")}")
    }
    if (finding.technicalDescription.isNotBlank()) {
        appendLine("Technical      : ${finding.technicalDescription}")
    }
    appendLine()

    appendLine("=== Confidence ===")
    appendLine("Level  : ${finding.confidence.name}")
    appendLine("Reason : ${finding.androidVisibilityLimits}")
    appendLine()

    appendLine("=== Recommended action ===")
    appendLine(finding.recommendedResponse)
    appendLine()

    appendLine("=== What could change the conclusion ===")
    appendLine(finding.verificationMethod)
    if (finding.threatIntelReferences.isNotEmpty()) {
        appendLine("References: ${finding.threatIntelReferences.joinToString(", ")}")
    }
}

package com.coldboar.coreguard

/**
 * Guardian rank derived from the numeric score. Ranks borrow the language of
 * ancient protection: AEGIS (the shield of Zeus) down to BREACHED.
 */
enum class GuardianRank {
    /** 90–100: every ward holds. */
    AEGIS,

    /** 65–89: minor weaknesses, still protected. */
    WARDED,

    /** 35–64: meaningful gaps in protection. */
    EXPOSED,

    /** 0–34: high-risk indicators present. */
    BREACHED;

    /** Plain-language status for first-time users. */
    val userLabel: String
        get() = when (this) {
            AEGIS -> "Strong protection"
            WARDED -> "Mostly protected"
            EXPOSED -> "Needs attention"
            BREACHED -> "High risk"
        }

    /** One-line guidance tied to the rank. */
    val userGuidance: String
        get() = when (this) {
            AEGIS -> "Your device looks solid. Keep scanning periodically."
            WARDED -> "A few checks need review — open the list below."
            EXPOSED -> "Important gaps found. Review warnings and run a privacy check."
            BREACHED -> "Serious risks detected. Review failed checks and run a privacy check now."
        }
}

/**
 * Computes a 0–100 Guardian Score from a set of security check results.
 *
 * Scoring: each check contributes an equal share of 100 points.
 * PASS earns the full share, WARN earns half, FAIL earns nothing.
 * An empty result list scores 0 (nothing verified means nothing earned).
 */
/**
 * How a single check result should be interpreted in the UI.
 * Lore/branding labels must never appear here as technical evidence.
 */
enum class EvidenceKind {
    /** Cryptographic or OS-attested signal (e.g. signing cert match). */
    VERIFIED,
    /** Best-effort heuristic (root paths, overlays, behavioral samples). */
    HEURISTIC,
    /** Teaching / policy guidance, not a live detection. */
    EDUCATIONAL
}

/** Per-check explanation for Guardian Score transparency. */
data class GuardianScoreEvidence(
    val checkId: String,
    val displayName: String,
    val state: SecurityCheckState,
    val explanation: String,
    val severity: SecurityCheckState,
    val confidence: EvidenceKind,
    val recommendedAction: String,
    val timestampMs: Long
)

object GuardianScore {

    fun compute(results: List<SecurityCheckResult>): Int {
        if (results.isEmpty()) return 0
        val earned = results.sumOf { result ->
            when (result.state) {
                SecurityCheckState.PASS -> 1.0
                SecurityCheckState.WARN -> 0.5
                SecurityCheckState.FAIL -> 0.0
            }
        }
        return Math.round(earned / results.size * 100).toInt()
    }

    fun rankFor(score: Int): GuardianRank = when {
        score >= 90 -> GuardianRank.AEGIS
        score >= 65 -> GuardianRank.WARDED
        score >= 35 -> GuardianRank.EXPOSED
        else -> GuardianRank.BREACHED
    }

    /**
     * Builds explainable evidence rows. Confidence is heuristic by default;
     * signature / attestation-style ids are marked VERIFIED.
     */
    fun explain(
        results: List<SecurityCheckResult>,
        timestampMs: Long = System.currentTimeMillis()
    ): List<GuardianScoreEvidence> =
        results.map { r ->
            val kind = when {
                r.id.contains("signature") || r.id.contains("attestation") ||
                    r.id.contains("play_integrity") -> EvidenceKind.VERIFIED
                r.id.contains("guide") || r.id.contains("policy") -> EvidenceKind.EDUCATIONAL
                else -> EvidenceKind.HEURISTIC
            }
            val action = when (r.state) {
                SecurityCheckState.PASS -> "No action needed for this check."
                SecurityCheckState.WARN -> "Review the explanation and harden if the risk applies."
                SecurityCheckState.FAIL -> "Address this finding before handling sensitive data."
            }
            GuardianScoreEvidence(
                checkId = r.id,
                displayName = r.displayName,
                state = r.state,
                explanation = r.explanation,
                severity = r.state,
                confidence = kind,
                recommendedAction = action,
                timestampMs = timestampMs
            )
        }
}

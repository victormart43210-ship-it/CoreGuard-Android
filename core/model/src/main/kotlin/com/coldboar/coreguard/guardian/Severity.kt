package com.coldboar.coreguard.guardian

/**
 * Calm user-facing severity (Blueprint §2.2). Prefer these labels over
 * “hacked”, “spyware confirmed”, or “Pegasus detected” unless evidence is verified.
 */
enum class Severity {
    PROTECTED,
    INFORMATIONAL,
    REVIEW_SUGGESTED,
    ELEVATED_CONCERN,
    HIGH_CONFIDENCE_RISK;

    val userLabel: String
        get() = when (this) {
            PROTECTED -> "Protected"
            INFORMATIONAL -> "Informational"
            REVIEW_SUGGESTED -> "Review Suggested"
            ELEVATED_CONCERN -> "Elevated Concern"
            HIGH_CONFIDENCE_RISK -> "High Confidence Risk"
        }
}

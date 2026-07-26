package com.coldboar.coreguard

/**
 * Represents the result state of a single security check.
 */
enum class SecurityCheckState {
    /** The check passed – no concerning indicator detected. */
    PASS,

    /** A notable condition exists but is not necessarily a breach. */
    WARN,

    /** A high-risk indicator was detected. */
    FAIL
}

/**
 * The result of evaluating a single security check.
 *
 * @param id          Stable identifier used in tests and logging.
 * @param displayName Short human-readable name shown in the UI.
 * @param state       PASS / WARN / FAIL outcome.
 * @param explanation One-sentence explanation of what the state means for the user.
 */
data class SecurityCheckResult(
    val id: String,
    val displayName: String,
    val state: SecurityCheckState,
    val explanation: String
)

/**
 * Contract for a single security check evaluator.
 *
 * Implementations must be deterministic and must NOT perform network I/O.
 * They should be unit-testable by injecting fake inputs via constructor parameters.
 */
interface SecurityCheckEvaluator {
    fun evaluate(): SecurityCheckResult
}

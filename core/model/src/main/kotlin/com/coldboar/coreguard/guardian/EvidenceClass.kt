package com.coldboar.coreguard.guardian

/**
 * Origin class for a security result (Guardian Intelligence Blueprint §2.1 / §5).
 *
 * Distinct from [com.coldboar.coreguard.EvidenceKind], which blends certainty with origin.
 * UI must never present [INFERRED] or [SIMULATED] as a confirmed observation.
 */
enum class EvidenceClass {
    /** Directly read from an Android API, package metadata, file, or OS source. */
    OBSERVED,

    /** Calculated from multiple observations or behavior patterns. */
    INFERRED,

    /** Educational, demonstration, laboratory, or fictional data. */
    SIMULATED,

    /** Android does not expose the required information to this application. */
    UNAVAILABLE,

    /** Entered or confirmed by the user. */
    USER_REPORTED;

    /** Short user-facing label for Truth Seals. */
    val userLabel: String
        get() = when (this) {
            OBSERVED -> "Observed"
            INFERRED -> "Inferred"
            SIMULATED -> "Simulation"
            UNAVAILABLE -> "Unavailable"
            USER_REPORTED -> "User reported"
        }
}

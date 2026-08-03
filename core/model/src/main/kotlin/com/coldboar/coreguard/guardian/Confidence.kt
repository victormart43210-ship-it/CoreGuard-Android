package com.coldboar.coreguard.guardian

/**
 * Certainty of a finding — independent from [Severity].
 * A severe condition can still have [LOW] confidence.
 */
enum class Confidence {
    LOW,
    MEDIUM,
    HIGH,
    VERIFIED;

    val userLabel: String
        get() = when (this) {
            LOW -> "Low confidence"
            MEDIUM -> "Medium confidence"
            HIGH -> "High confidence"
            VERIFIED -> "Verified"
        }
}

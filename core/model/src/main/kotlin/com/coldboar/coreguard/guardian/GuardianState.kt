package com.coldboar.coreguard.guardian

/** Central posture for Guardian Pulse (Blueprint §8). */
enum class GuardianState {
    PROTECTED,
    OBSERVING,
    ATTENTION_REQUIRED,
    HIGH_RISK,
    SCANNING;

    val userLabel: String
        get() = when (this) {
            PROTECTED -> "Protected"
            OBSERVING -> "Observing"
            ATTENTION_REQUIRED -> "Attention required"
            HIGH_RISK -> "High risk"
            SCANNING -> "Scanning"
        }

    val guidance: String
        get() = when (this) {
            PROTECTED -> "No meaningful active concerns from completed checks."
            OBSERVING -> "Monitoring is healthy; some data is incomplete or still settling."
            ATTENTION_REQUIRED -> "Review suggested findings below. CoreGuard is not claiming compromise."
            HIGH_RISK -> "High-confidence risk indicators need your review. Evidence is listed below."
            SCANNING -> "A user-requested scan is in progress."
        }
}

/** Whether a user-requested scan is running. */
enum class ScanState {
    IDLE,
    RUNNING
}

/** Completeness of observation inputs for pulse resolution. */
enum class DataAvailability {
    COMPLETE,
    PARTIAL,
    NONE
}

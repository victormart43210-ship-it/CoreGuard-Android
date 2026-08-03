package com.coldboar.coreguard.guardian

/**
 * Deterministic Guardian Pulse state (Blueprint §8.2).
 * Never maps “no permission / no data” to [GuardianState.PROTECTED].
 */
object GuardianStateResolver {

    fun resolve(
        findings: List<SecurityFinding>,
        scanState: ScanState,
        dataAvailability: DataAvailability
    ): GuardianState {
        if (scanState == ScanState.RUNNING) return GuardianState.SCANNING

        if (dataAvailability == DataAvailability.NONE) {
            return GuardianState.OBSERVING
        }

        val active = findings.filter { it.active }
        val highRisk = active.any {
            it.severity == Severity.HIGH_CONFIDENCE_RISK &&
                (it.confidence == Confidence.HIGH || it.confidence == Confidence.VERIFIED)
        }
        if (highRisk) return GuardianState.HIGH_RISK

        val attention = active.any {
            it.severity == Severity.ELEVATED_CONCERN ||
                it.severity == Severity.REVIEW_SUGGESTED
        } || active.count { it.severity >= Severity.REVIEW_SUGGESTED } >= 2

        if (attention) return GuardianState.ATTENTION_REQUIRED

        if (dataAvailability == DataAvailability.PARTIAL) {
            return GuardianState.OBSERVING
        }

        return GuardianState.PROTECTED
    }
}

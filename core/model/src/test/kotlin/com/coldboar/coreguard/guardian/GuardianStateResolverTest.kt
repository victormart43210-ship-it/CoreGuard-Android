package com.coldboar.coreguard.guardian

import org.junit.Assert.assertEquals
import org.junit.Test

class GuardianStateResolverTest {

    private fun finding(
        severity: Severity,
        confidence: Confidence,
        active: Boolean = true,
        category: FindingCategory = FindingCategory.DEVICE_INTEGRITY
    ): SecurityFinding {
        val now = 1L
        return SecurityFinding(
            id = "f-${severity.name}-${confidence.name}",
            category = category,
            severity = severity,
            confidence = confidence,
            title = "t",
            plainLanguageSummary = "s",
            whyItMatters = "w",
            possibleBenignCauses = listOf("b"),
            evidence = listOf(
                Evidence(
                    id = "e",
                    evidenceClass = EvidenceClass.INFERRED,
                    source = "test",
                    summary = "sum",
                    collectedAtEpochMillis = now
                )
            ),
            recommendedActions = emptyList(),
            firstSeenEpochMillis = now,
            lastSeenEpochMillis = now,
            active = active,
            detectorVersion = "t"
        )
    }

    @Test
    fun scanningWins() {
        assertEquals(
            GuardianState.SCANNING,
            GuardianStateResolver.resolve(emptyList(), ScanState.RUNNING, DataAvailability.COMPLETE)
        )
    }

    @Test
    fun noDataIsObservingNotProtected() {
        assertEquals(
            GuardianState.OBSERVING,
            GuardianStateResolver.resolve(emptyList(), ScanState.IDLE, DataAvailability.NONE)
        )
    }

    @Test
    fun highConfidenceRisk() {
        val f = finding(Severity.HIGH_CONFIDENCE_RISK, Confidence.VERIFIED)
        assertEquals(
            GuardianState.HIGH_RISK,
            GuardianStateResolver.resolve(listOf(f), ScanState.IDLE, DataAvailability.COMPLETE)
        )
    }

    @Test
    fun protectedWhenClean() {
        val f = finding(Severity.PROTECTED, Confidence.MEDIUM, active = false)
        assertEquals(
            GuardianState.PROTECTED,
            GuardianStateResolver.resolve(listOf(f), ScanState.IDLE, DataAvailability.COMPLETE)
        )
    }
}

package com.coldboar.coreguard.guardian

import com.coldboar.coreguard.guardian.hardening.WardCircleEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WardCircleEvaluatorTest {

    @Test
    fun completionIgnoresManualUntilPassed() {
        val checks = listOf(
            HardeningCheck(
                id = "a",
                title = "a",
                description = "d",
                status = HardeningStatus.PASSED,
                evidenceClass = EvidenceClass.OBSERVED,
                importance = Severity.INFORMATIONAL,
                action = null,
                lastCheckedEpochMillis = 1L
            ),
            HardeningCheck(
                id = "b",
                title = "b",
                description = "d",
                status = HardeningStatus.MANUAL_CONFIRMATION_REQUIRED,
                evidenceClass = EvidenceClass.USER_REPORTED,
                importance = Severity.INFORMATIONAL,
                action = null,
                lastCheckedEpochMillis = 1L
            )
        )
        assertEquals(50, WardCircleEvaluator.completionPercent(checks))
        assertTrue(WardCircleEvaluator.completionPercent(emptyList()) == 0)
    }
}

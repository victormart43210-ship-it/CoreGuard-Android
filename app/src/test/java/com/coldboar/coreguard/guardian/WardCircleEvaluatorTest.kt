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

    @Test
    fun unavailableCheckDoesNotCountAsHardeningProgress() {
        // A setting Android refuses to expose must never be scored as secured.
        val checks = listOf(
            HardeningCheck(
                id = "ward.unknown_sources",
                title = "Install unknown apps",
                description = "Android did not allow CoreGuard to read this setting.",
                status = HardeningStatus.UNAVAILABLE,
                evidenceClass = EvidenceClass.UNAVAILABLE,
                importance = Severity.REVIEW_SUGGESTED,
                action = null,
                lastCheckedEpochMillis = 1L
            ),
            HardeningCheck(
                id = "ward.screen_lock",
                title = "Screen lock",
                description = "d",
                status = HardeningStatus.PASSED,
                evidenceClass = EvidenceClass.OBSERVED,
                importance = Severity.ELEVATED_CONCERN,
                action = null,
                lastCheckedEpochMillis = 1L
            )
        )
        assertEquals(50, WardCircleEvaluator.completionPercent(checks))
    }
}

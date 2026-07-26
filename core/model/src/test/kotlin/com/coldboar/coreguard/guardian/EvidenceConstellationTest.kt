package com.coldboar.coreguard.guardian

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidenceConstellationTest {

    private fun f(
        id: String,
        category: FindingCategory,
        confidence: Confidence = Confidence.MEDIUM
    ): SecurityFinding {
        val now = System.currentTimeMillis()
        return SecurityFinding(
            id = id,
            category = category,
            severity = Severity.REVIEW_SUGGESTED,
            confidence = confidence,
            title = id,
            plainLanguageSummary = "s",
            whyItMatters = "w",
            possibleBenignCauses = listOf("b"),
            evidence = listOf(
                Evidence("e-$id", EvidenceClass.INFERRED, "t", "s", collectedAtEpochMillis = now)
            ),
            recommendedActions = emptyList(),
            firstSeenEpochMillis = now,
            lastSeenEpochMillis = now,
            active = true,
            detectorVersion = "t"
        )
    }

    @Test
    fun sideloadRuleFiresWithTwoSignals() {
        val results = EvidenceConstellation.correlate(
            listOf(
                f("pkg", FindingCategory.PACKAGE_CHANGE),
                f("perm", FindingCategory.APP_PERMISSION)
            )
        )
        assertTrue(results.any { it.ruleId.contains("sideload") })
        results.forEach {
            assertTrue(it.confidence != Confidence.VERIFIED)
            assertEquals(EvidenceClass.INFERRED, it.evidenceClass)
        }
    }

    @Test
    fun expiredWindowYieldsNothing() {
        val old = System.currentTimeMillis() - 10L * 24 * 60 * 60 * 1000
        val stale = f("pkg", FindingCategory.PACKAGE_CHANGE).copy(
            firstSeenEpochMillis = old,
            lastSeenEpochMillis = old
        )
        val results = EvidenceConstellation.correlate(listOf(stale, f("a11y", FindingCategory.ACCESSIBILITY)))
        // a11y alone + stale package should not meet privilege rule (needs 3) or sideload (needs package in window)
        assertTrue(results.none { it.ruleId.contains("privilege") })
    }
}

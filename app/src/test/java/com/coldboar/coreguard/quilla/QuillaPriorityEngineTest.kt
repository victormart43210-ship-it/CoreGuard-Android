package com.coldboar.coreguard.quilla

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuillaPriorityEngineTest {

    @Test
    fun `infected verdict yields critical posture and scan move`() {
        val brief = QuillaPriorityEngine.brief(
            QuillaMemorySnapshot(
                lastScanVerdict = "INFECTED",
                lastScanDetections = 3,
                historyCount = 2,
                shieldActive = false
            )
        )
        assertEquals(QuillaPriorityEngine.Posture.CRITICAL, brief.posture)
        assertTrue(brief.score >= 80)
        assertTrue(brief.moves.any { it.id == QuillaActionSuggestion.RUN_SCAN })
        assertTrue(brief.headline.contains("CRITICAL"))
    }

    @Test
    fun `clean shielded device is steady`() {
        val brief = QuillaPriorityEngine.brief(
            QuillaMemorySnapshot(
                lastScanVerdict = "CLEAN",
                lastScanDetections = 0,
                historyCount = 4,
                shieldActive = true,
                shieldBlocked = 0
            ),
            QuillaResearchSnapshot(indicatorCount = 5, synced = true)
        )
        assertEquals(QuillaPriorityEngine.Posture.STEADY, brief.posture)
        assertTrue(brief.score < 45)
    }

    @Test
    fun `no scan baseline is unknown with establish-scan move`() {
        val brief = QuillaPriorityEngine.brief(QuillaMemorySnapshot())
        assertEquals(QuillaPriorityEngine.Posture.UNKNOWN, brief.posture)
        assertTrue(brief.moves.any { it.id == QuillaActionSuggestion.RUN_SCAN })
        assertTrue(brief.chipPrompts.isNotEmpty())
    }

    @Test
    fun `high telemetry raises score and move`() {
        val brief = QuillaPriorityEngine.brief(
            QuillaMemorySnapshot(
                lastScanVerdict = "CLEAN",
                historyCount = 2,
                shieldActive = true,
                telemetryDeltaCount = 4,
                telemetryHighSeverity = true
            )
        )
        assertTrue(brief.score >= 45)
        assertTrue(brief.moves.any { it.id == "telemetry" })
    }
}

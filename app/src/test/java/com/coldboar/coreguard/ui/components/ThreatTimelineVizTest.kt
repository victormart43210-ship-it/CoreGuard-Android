package com.coldboar.coreguard.ui.components

import com.coldboar.coreguard.mvt.ScanHistoryStore
import com.coldboar.coreguard.mvt.ScanVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreatTimelineVizTest {

    private fun record(
        verdict: ScanVerdict,
        detections: Int,
        ts: Long = 1L
    ) = ScanHistoryStore.ScanRecord(
        timestampMs = ts,
        verdict = verdict,
        scannedArtifacts = 10,
        indicatorCount = 5,
        durationMillis = 100,
        detectionCount = detections
    )

    @Test
    fun `chronological reverses newest-first history`() {
        val newestFirst = listOf(
            record(ScanVerdict.CLEAN, 0, ts = 3),
            record(ScanVerdict.SUSPICIOUS, 2, ts = 2),
            record(ScanVerdict.INFECTED, 4, ts = 1)
        )
        val chrono = ThreatTimelineViz.chronological(newestFirst)
        assertEquals(listOf(1L, 2L, 3L), chrono.map { it.timestampMs })
    }

    @Test
    fun `detection heights normalize to unit interval`() {
        val chrono = listOf(
            record(ScanVerdict.CLEAN, 0),
            record(ScanVerdict.SUSPICIOUS, 2),
            record(ScanVerdict.INFECTED, 4)
        )
        val heights = ThreatTimelineViz.detectionHeights(chrono)
        assertEquals(listOf(0f, 0.5f, 1f), heights)
    }

    @Test
    fun `verdict summary stays indicator-honest`() {
        val summary = ThreatTimelineViz.verdictSummary(
            listOf(
                record(ScanVerdict.CLEAN, 0),
                record(ScanVerdict.INFECTED, 3)
            )
        )
        assertTrue(summary.contains("indicators matched"))
        assertTrue(summary.contains("3 total flags"))
        assertTrue(!summary.contains("guaranteed", ignoreCase = true))
    }
}

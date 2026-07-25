package com.coldboar.coreguard.quilla

import com.coldboar.coreguard.mvt.ScanReport
import com.coldboar.coreguard.mvt.ScanVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuillaInsightTest {

    private fun cleanReport() = ScanReport(
        startedAtMillis = 0L,
        finishedAtMillis = 10L,
        scannedPackages = 5,
        scannedProcesses = 0,
        scannedFiles = 0,
        indicatorCount = 3,
        detections = emptyList()
    )

    @Test
    fun `postScan clean suggests shield when shield off`() {
        val card = QuillaInsight.postScanCard(cleanReport(), shieldActive = false)
        assertEquals(QuillaInsight.Action.OPEN_SHIELD, card.primaryAction)
        assertTrue(card.title.contains("Quilla", ignoreCase = true))
    }

    @Test
    fun `postScan clean with shield asks quilla`() {
        val card = QuillaInsight.postScanCard(cleanReport(), shieldActive = true)
        assertEquals(QuillaInsight.Action.ASK_QUILLA, card.primaryAction)
    }
}

package com.coldboar.coreguard.quilla

import com.coldboar.coreguard.mvt.ArtifactKind
import com.coldboar.coreguard.mvt.Detection
import com.coldboar.coreguard.mvt.Indicator
import com.coldboar.coreguard.mvt.IndicatorType
import com.coldboar.coreguard.mvt.ScanReport
import com.coldboar.coreguard.mvt.ScanVerdict
import com.coldboar.coreguard.mvt.ThreatSeverity
import com.coldboar.coreguard.ui.navigation.QuillaActionRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    private fun suspiciousReport() = ScanReport(
        startedAtMillis = 0L,
        finishedAtMillis = 10L,
        scannedPackages = 5,
        scannedProcesses = 0,
        scannedFiles = 0,
        indicatorCount = 3,
        detections = listOf(
            Detection(
                kind = ArtifactKind.DOMAIN,
                artifact = "bad.example",
                indicator = Indicator(
                    type = IndicatorType.DOMAIN,
                    value = "bad.example",
                    malware = "test"
                ),
                severity = ThreatSeverity.MEDIUM
            )
        )
    )

    private fun infectedReport() = ScanReport(
        startedAtMillis = 0L,
        finishedAtMillis = 10L,
        scannedPackages = 5,
        scannedProcesses = 0,
        scannedFiles = 0,
        indicatorCount = 3,
        detections = listOf(
            Detection(
                kind = ArtifactKind.PACKAGE,
                artifact = "com.evil.app",
                indicator = Indicator(
                    type = IndicatorType.PACKAGE,
                    value = "com.evil.app",
                    malware = "pegasus"
                ),
                severity = ThreatSeverity.CRITICAL
            )
        )
    )

    @Test
    fun `postScan clean suggests shield when shield off`() {
        val card = QuillaInsight.postScanCard(cleanReport(), shieldActive = false)
        assertEquals(QuillaInsight.Action.OPEN_SHIELD, card.primaryAction)
        assertEquals(QuillaInsight.Action.OPEN_TIMELINE, card.secondaryAction)
        assertTrue(card.title.contains("Quilla", ignoreCase = true))
        assertEquals(
            QuillaActionRouter.Destination.SHIELD,
            QuillaActionRouter.destinationFor(card.primaryAction)
        )
    }

    @Test
    fun `postScan clean with shield asks quilla`() {
        val card = QuillaInsight.postScanCard(cleanReport(), shieldActive = true)
        assertEquals(QuillaInsight.Action.ASK_QUILLA, card.primaryAction)
        assertEquals(
            QuillaActionRouter.Destination.SETTINGS_QUILLA,
            QuillaActionRouter.destinationFor(card.primaryAction)
        )
    }

    @Test
    fun `postScan suspicious routes to shield or quilla based on shield state`() {
        assertEquals(ScanVerdict.SUSPICIOUS, suspiciousReport().verdict)
        val off = QuillaInsight.postScanCard(suspiciousReport(), shieldActive = false)
        assertEquals(QuillaInsight.Action.OPEN_SHIELD, off.primaryAction)
        assertEquals(QuillaInsight.Action.ASK_QUILLA, off.secondaryAction)

        val on = QuillaInsight.postScanCard(suspiciousReport(), shieldActive = true)
        assertEquals(QuillaInsight.Action.ASK_QUILLA, on.primaryAction)
    }

    @Test
    fun `postScan infected always prioritizes shield then quilla`() {
        assertEquals(ScanVerdict.INFECTED, infectedReport().verdict)
        val card = QuillaInsight.postScanCard(infectedReport(), shieldActive = false)
        assertEquals(QuillaInsight.Action.OPEN_SHIELD, card.primaryAction)
        assertEquals(QuillaInsight.Action.ASK_QUILLA, card.secondaryAction)
        assertTrue(card.body.contains("Shield", ignoreCase = true))
    }

    @Test
    fun `home card with no scan evidence routes to scanner`() {
        val card = QuillaInsight.homeCardFromMemory(QuillaMemorySnapshot())
        assertEquals(QuillaInsight.Action.RUN_SCAN, card.primaryAction)
        assertEquals(QuillaInsight.Action.ASK_QUILLA, card.secondaryAction)
        assertEquals(
            QuillaActionRouter.Destination.SCANNER,
            QuillaActionRouter.destinationFor(card.primaryAction)
        )
    }

    @Test
    fun `home card clean without shield routes to shield`() {
        val card = QuillaInsight.homeCardFromMemory(
            QuillaMemorySnapshot(lastScanVerdict = ScanVerdict.CLEAN.name, shieldActive = false)
        )
        assertEquals(QuillaInsight.Action.OPEN_SHIELD, card.primaryAction)
        assertEquals(QuillaInsight.Action.ASK_QUILLA, card.secondaryAction)
    }

    @Test
    fun `home card clean with shield routes to quilla and timeline`() {
        val card = QuillaInsight.homeCardFromMemory(
            QuillaMemorySnapshot(
                lastScanVerdict = ScanVerdict.CLEAN.name,
                shieldActive = true,
                shieldBlocked = 4
            )
        )
        assertEquals(QuillaInsight.Action.ASK_QUILLA, card.primaryAction)
        assertEquals(QuillaInsight.Action.OPEN_TIMELINE, card.secondaryAction)
        assertTrue(card.body.contains("4"))
    }

    @Test
    fun `home card suspicious without shield offers shield secondary`() {
        val card = QuillaInsight.homeCardFromMemory(
            QuillaMemorySnapshot(
                lastScanVerdict = ScanVerdict.SUSPICIOUS.name,
                shieldActive = false
            ),
            answerBody = "Review Scanner findings."
        )
        assertEquals(QuillaInsight.Action.RUN_SCAN, card.primaryAction)
        assertEquals(QuillaInsight.Action.OPEN_SHIELD, card.secondaryAction)
        assertEquals("Review Scanner findings.", card.body)
    }

    @Test
    fun `shield card recommends enable when last scan was not clean`() {
        val card = QuillaInsight.shieldCardFromMemory(
            QuillaMemorySnapshot(shieldActive = false),
            lastReport = suspiciousReport()
        )
        assertNull(card.primaryCta)
        assertEquals(QuillaInsight.Action.OPEN_SHIELD, card.primaryAction)
    }

    @Test
    fun `shield card with blocks routes to scanner`() {
        val card = QuillaInsight.shieldCardFromMemory(
            QuillaMemorySnapshot(
                shieldActive = true,
                shieldBlocked = 12,
                lastBlockedDomain = "tracker.example"
            ),
            lastReport = cleanReport()
        )
        assertEquals(QuillaInsight.Action.RUN_SCAN, card.primaryAction)
        assertEquals(QuillaInsight.Action.ASK_QUILLA, card.secondaryAction)
        assertTrue(card.body.contains("tracker.example"))
        assertEquals(
            QuillaActionRouter.Destination.SCANNER,
            QuillaActionRouter.destinationFor(card.primaryAction)
        )
    }

    @Test
    fun `shield idle card asks quilla`() {
        val card = QuillaInsight.shieldCardFromMemory(
            QuillaMemorySnapshot(shieldActive = false),
            lastReport = cleanReport()
        )
        assertEquals(QuillaInsight.Action.ASK_QUILLA, card.primaryAction)
        assertEquals(
            QuillaActionRouter.Destination.SETTINGS_QUILLA,
            QuillaActionRouter.destinationFor(card.primaryAction)
        )
    }
}

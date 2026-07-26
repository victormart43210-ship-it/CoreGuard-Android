package com.coldboar.coreguard.quilla

import com.coldboar.coreguard.mvt.ArtifactKind
import com.coldboar.coreguard.mvt.Detection
import com.coldboar.coreguard.mvt.Indicator
import com.coldboar.coreguard.mvt.IndicatorType
import com.coldboar.coreguard.mvt.ScanReport
import com.coldboar.coreguard.mvt.ThreatSeverity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuillaSalesCoachTest {

    @Test
    fun `premium question upsells free users`() {
        val answer = QuillaSalesCoach.answer(
            "Is Premium worth it?",
            QuillaSalesCoach.DeviceContext(isPremium = false)
        )
        assertTrue(answer.suggestPremium)
        assertTrue(answer.premiumPitch!!.contains("signature"))
    }

    @Test
    fun `premium users are not upsold on premium question`() {
        val answer = QuillaSalesCoach.answer(
            "upgrade",
            QuillaSalesCoach.DeviceContext(isPremium = true)
        )
        assertFalse(answer.suggestPremium)
    }

    @Test
    fun `export coach points free users at Premium gate`() {
        val answer = QuillaSalesCoach.answer(
            "export compliance json",
            QuillaSalesCoach.DeviceContext(isPremium = false)
        )
        assertTrue(answer.suggestPremium)
        assertTrue(answer.text.contains("Premium", ignoreCase = true))
    }

    @Test
    fun `status coach asks for scan when no evidence`() {
        val answer = QuillaSalesCoach.answer(
            "am I safe?",
            QuillaSalesCoach.DeviceContext(isPremium = false, lastScan = null)
        )
        assertTrue(answer.text.contains("Nemesis", ignoreCase = true) || answer.text.contains("scan", ignoreCase = true))
    }

    @Test
    fun `suspicious scan can trigger premium pitch`() {
        val detection = Detection(
            kind = ArtifactKind.DOMAIN,
            artifact = "example.test",
            indicator = Indicator(
                type = IndicatorType.DOMAIN,
                value = "example.test",
                malware = "test"
            ),
            severity = ThreatSeverity.MEDIUM
        )
        val report = ScanReport(
            startedAtMillis = 0L,
            finishedAtMillis = 12L,
            scannedPackages = 10,
            scannedProcesses = 0,
            scannedFiles = 0,
            indicatorCount = 5,
            detections = listOf(detection)
        )
        val answer = QuillaSalesCoach.answer(
            "scan risk score",
            QuillaSalesCoach.DeviceContext(isPremium = false, lastScan = report)
        )
        assertTrue(answer.suggestPremium)
        assertTrue(answer.premiumPitch!!.isNotBlank())
    }

    @Test
    fun `premium user status coach never upsells`() {
        val report = ScanReport(
            startedAtMillis = 0L,
            finishedAtMillis = 12L,
            scannedPackages = 10,
            scannedProcesses = 0,
            scannedFiles = 0,
            indicatorCount = 5,
            detections = listOf(
                Detection(
                    kind = ArtifactKind.DOMAIN,
                    artifact = "example.test",
                    indicator = Indicator(
                        type = IndicatorType.DOMAIN,
                        value = "example.test",
                        malware = "test"
                    ),
                    severity = ThreatSeverity.MEDIUM
                )
            )
        )
        val answer = QuillaSalesCoach.answer(
            "scan risk score",
            QuillaSalesCoach.DeviceContext(isPremium = true, lastScan = report)
        )
        assertFalse(answer.suggestPremium)
        assertTrue(answer.premiumPitch == null)
    }

    @Test
    fun `signature coach gates live refresh behind premium`() {
        val free = QuillaSalesCoach.answer(
            "refresh ioc signatures",
            QuillaSalesCoach.DeviceContext(isPremium = false)
        )
        assertTrue(free.suggestPremium)
        assertTrue(free.premiumPitch!!.contains("signature", ignoreCase = true))

        val premium = QuillaSalesCoach.answer(
            "refresh ioc signatures",
            QuillaSalesCoach.DeviceContext(isPremium = true)
        )
        assertFalse(premium.suggestPremium)
        assertTrue(premium.text.contains("Refresh threat signatures", ignoreCase = true))
    }

    @Test
    fun `timeline coach reports free vs premium history depth`() {
        val free = QuillaSalesCoach.answer(
            "open timeline history",
            QuillaSalesCoach.DeviceContext(isPremium = false, timelineCount = 3)
        )
        assertTrue(free.suggestPremium)
        assertTrue(free.text.contains("3"))

        val premium = QuillaSalesCoach.answer(
            "open timeline history",
            QuillaSalesCoach.DeviceContext(isPremium = true, timelineCount = 10)
        )
        assertFalse(premium.suggestPremium)
        assertTrue(premium.text.contains("Premium", ignoreCase = true))
    }
}

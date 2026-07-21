package com.coldboar.coreguard.mvt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NemesisScannerTest {

    private val matcher = IocMatcher(
        listOf(
            Indicator(IndicatorType.PACKAGE, "com.network.android", "Chrysaor"),
            Indicator(IndicatorType.PROCESS, "pegasus-implant", "Pegasus"),
            Indicator(IndicatorType.FILEPATH, "/data/local/tmp/implant.so", "Pegasus")
        )
    )

    @Test
    fun `clean device yields CLEAN verdict`() {
        val scanner = NemesisScanner(
            matcher = matcher,
            installedPackages = { listOf("com.android.chrome", "com.coldboar.coreguard") },
            runningProcesses = { listOf("system_server", "zygote") },
            accessibleFiles = { listOf("/sdcard/photo.jpg") }
        )
        val report = scanner.scan()
        assertEquals(ScanVerdict.CLEAN, report.verdict)
        assertTrue(report.detections.isEmpty())
        assertEquals(2, report.scannedPackages)
    }

    @Test
    fun `malicious package yields INFECTED with critical detection`() {
        val scanner = NemesisScanner(
            matcher = matcher,
            installedPackages = { listOf("com.android.chrome", "com.network.android") }
        )
        val report = scanner.scan()
        assertEquals(ScanVerdict.INFECTED, report.verdict)
        assertEquals(1, report.detections.size)
        assertEquals(ArtifactKind.PACKAGE, report.detections.first().kind)
        assertEquals(ThreatSeverity.CRITICAL, report.detections.first().severity)
    }

    @Test
    fun `malicious process is detected`() {
        val scanner = NemesisScanner(
            matcher = matcher,
            installedPackages = { emptyList() },
            runningProcesses = { listOf("/system/bin/pegasus-implant") }
        )
        val report = scanner.scan()
        assertEquals(ScanVerdict.INFECTED, report.verdict)
        assertEquals(ArtifactKind.PROCESS, report.detections.first().kind)
    }

    @Test
    fun `malicious file yields SUSPICIOUS when only high severity`() {
        val scanner = NemesisScanner(
            matcher = matcher,
            installedPackages = { emptyList() },
            accessibleFiles = { listOf("/data/local/tmp/implant.so") }
        )
        val report = scanner.scan()
        assertEquals(ScanVerdict.SUSPICIOUS, report.verdict)
        assertEquals(ThreatSeverity.HIGH, report.detections.first().severity)
    }

    @Test
    fun `classify prioritises critical over high`() {
        val critical = Detection(
            ArtifactKind.PACKAGE, "x",
            Indicator(IndicatorType.PACKAGE, "x", "m"), ThreatSeverity.CRITICAL
        )
        val high = Detection(
            ArtifactKind.FILE, "y",
            Indicator(IndicatorType.FILEPATH, "y", "m"), ThreatSeverity.HIGH
        )
        assertEquals(ScanVerdict.INFECTED, NemesisScanner.classify(listOf(high, critical)))
        assertEquals(ScanVerdict.SUSPICIOUS, NemesisScanner.classify(listOf(high)))
        assertEquals(ScanVerdict.CLEAN, NemesisScanner.classify(emptyList()))
    }
}

package com.coldboar.coreguard.defense

import com.coldboar.coreguard.SecurityCheckResult
import com.coldboar.coreguard.SecurityCheckState
import com.coldboar.coreguard.quilla.QuillaMemorySnapshot
import com.coldboar.coreguard.quilla.QuillaResearchSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AngelicDefenseBlessingsTest {

    @Test
    fun `choir has ten angelic blessings`() {
        val report = AngelicDefenseBlessings.evaluate(emptyList())
        assertEquals(10, report.blessings.size)
        assertTrue(report.blessings.any { it.angel == "Michael" })
        assertTrue(report.blessings.any { it.angel == "Sandalphon" })
    }

    @Test
    fun `michael breaches on frida fail`() {
        val checks = listOf(
            SecurityCheckResult("frida", "Frida", SecurityCheckState.FAIL, "Frida port open"),
            SecurityCheckResult("hook_maps", "Hooks", SecurityCheckState.PASS, "clean"),
            SecurityCheckResult("memory_integrity", "Mem", SecurityCheckState.PASS, "ok"),
            SecurityCheckResult("mount_integrity", "Mount", SecurityCheckState.PASS, "ok"),
            SecurityCheckResult("root", "Root", SecurityCheckState.PASS, "ok")
        )
        val report = AngelicDefenseBlessings.evaluate(checks)
        val michael = report.blessings.first { it.angel == "Michael" }
        assertEquals(AngelicDefenseBlessings.BlessingState.BREACHED, michael.state)
        assertTrue(report.breachedCount >= 1)
    }

    @Test
    fun `sandalphon watches overlay warn`() {
        val checks = listOf(
            SecurityCheckResult("overlay_abuse", "Overlay", SecurityCheckState.WARN, "2 overlays"),
            SecurityCheckResult("accessibility_abuse", "A11y", SecurityCheckState.PASS, "none"),
            SecurityCheckResult("sideload_risk", "Sideload", SecurityCheckState.PASS, "play")
        )
        val report = AngelicDefenseBlessings.evaluate(checks)
        val sandalphon = report.blessings.first { it.angel == "Sandalphon" }
        assertEquals(AngelicDefenseBlessings.BlessingState.WATCHING, sandalphon.state)
    }

    @Test
    fun `tzadkiel watches suspicious Nemesis verdict from Memory`() {
        val report = AngelicDefenseBlessings.evaluate(
            checks = listOf(
                SecurityCheckResult(
                    "spyware_scan",
                    "Privacy Integrity",
                    SecurityCheckState.WARN,
                    "Last check flagged 1 item(s)."
                )
            ),
            memory = QuillaMemorySnapshot(
                lastScanVerdict = "SUSPICIOUS",
                lastScanDetections = 1,
                historyCount = 1
            ),
            research = QuillaResearchSnapshot()
        )
        val tzadkiel = report.blessings.first { it.angel == "Tzadkiel" }
        assertEquals(AngelicDefenseBlessings.BlessingState.WATCHING, tzadkiel.state)
        assertTrue(tzadkiel.detail.contains("Nemesis") || tzadkiel.detail.contains("flagged"))
    }

    @Test
    fun `kamael active when shield on`() {
        val report = AngelicDefenseBlessings.evaluate(
            checks = emptyList(),
            memory = QuillaMemorySnapshot(shieldActive = true, shieldBlocked = 0),
            research = QuillaResearchSnapshot()
        )
        val kamael = report.blessings.first { it.angel == "Kamael" }
        assertEquals(AngelicDefenseBlessings.BlessingState.ACTIVE, kamael.state)
        assertEquals("Raagiosl", kamael.enochianKing)
        assertEquals("West/Water", kamael.watchtower)
    }

    @Test
    fun `michael carries fire tablet names`() {
        val report = AngelicDefenseBlessings.evaluate(emptyList())
        val michael = report.blessings.first { it.angel == "Michael" }
        assertEquals("Edelperna", michael.enochianKing)
        assertEquals("Habioro", michael.enochianSenior)
        assertTrue(report.sealLine.contains("Watchtowers"))
    }
}

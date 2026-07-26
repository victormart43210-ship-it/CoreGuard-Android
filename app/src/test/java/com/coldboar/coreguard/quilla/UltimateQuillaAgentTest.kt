package com.coldboar.coreguard.quilla

import com.coldboar.coreguard.mvt.ArtifactKind
import com.coldboar.coreguard.mvt.Detection
import com.coldboar.coreguard.mvt.Indicator
import com.coldboar.coreguard.mvt.IndicatorType
import com.coldboar.coreguard.mvt.ScanReport
import com.coldboar.coreguard.mvt.ThreatSeverity
import com.coldboar.coreguard.quilla.knowledge.CyberKnowledgeBase
import com.coldboar.coreguard.ui.navigation.QuillaActionRouter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class UltimateQuillaAgentTest {

    private val memory = QuillaMemorySnapshot(
        lastScanVerdict = "SUSPICIOUS",
        lastScanDetections = 2,
        historyCount = 3,
        shieldActive = false,
        shieldBlocked = 0,
        activeHypotheses = listOf("Possible IOC match on com.example.bad")
    )

    private val research = QuillaResearchSnapshot(
        indicatorCount = 12,
        synced = true,
        sourceLabel = "Amnesty STIX2"
    )

    private val suspiciousScan = ScanReport(
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

    private lateinit var agent: UltimateQuillaAgent

    @Before
    fun setUp() {
        CyberKnowledgeBase.clear()
        val dir = File("src/main/assets/knowledge")
        val docs = dir.listFiles { f -> f.extension == "json" && f.name != "manifest.json" }
            ?.map { it.readText() }
            .orEmpty()
        CyberKnowledgeBase.loadDocuments(docs)
        agent = UltimateQuillaAgent(
            memoryProvider = { memory },
            researchProvider = { research },
            isPremiumProvider = { false },
            lastScanProvider = { suspiciousScan }
        )
    }

    @After
    fun tearDown() {
        CyberKnowledgeBase.clear()
    }

    @Test
    fun `classifies capabilities and exposes all modules`() {
        val answer = agent.answer("what can you do as an ultimate agent?")
        assertEquals(QuillaIntent.CAPABILITIES, answer.intent)
        assertEquals(QuillaModule.entries.toSet(), answer.modulesUsed.toSet())
        assertTrue(answer.text.contains("Brain"))
        assertTrue(answer.text.contains("Memory"))
        assertTrue(answer.text.contains("Research"))
        assertTrue(answer.text.contains("Knowledge"))
        assertTrue(answer.text.contains("Actions"))
        assertTrue(answer.text.contains("Tools"))
        assertFalse(answer.text.lowercase().contains("chatgpt api key"))
    }

    @Test
    fun `status cites memory scan and shield`() {
        val answer = agent.answer("am I safe right now?")
        assertEquals(QuillaIntent.STATUS, answer.intent)
        assertTrue(answer.text.contains("SUSPICIOUS"))
        assertTrue(answer.text.contains("Shield is OFF"))
        assertTrue(answer.actions.any { it.id == QuillaActionSuggestion.RUN_SCAN })
    }

    @Test
    fun `scan intent suggests nemesis action`() {
        val answer = agent.answer("please run a nemesis scan")
        assertEquals(QuillaIntent.SCAN, answer.intent)
        assertTrue(answer.actions.any { it.id == QuillaActionSuggestion.RUN_SCAN })
        assertTrue(answer.modulesUsed.contains(QuillaModule.TOOLS))
    }

    @Test
    fun `shield intent refuses silent vpn enable`() {
        val answer = agent.answer("turn on the privacy shield vpn")
        assertEquals(QuillaIntent.SHIELD, answer.intent)
        assertTrue(answer.text.lowercase().contains("vpn consent"))
        assertTrue(answer.actions.any { it.id == QuillaActionSuggestion.OPEN_SHIELD })
    }

    @Test
    fun `research intent reports intel snapshot`() {
        val answer = agent.answer("sync threat intel research from amnesty")
        assertEquals(QuillaIntent.RESEARCH, answer.intent)
        assertTrue(answer.text.contains("12"))
        assertTrue(answer.text.contains("Amnesty STIX2"))
        assertTrue(answer.modulesUsed.contains(QuillaModule.RESEARCH))
    }

    @Test
    fun `knowledge intent cites cyber codex`() {
        val answer = agent.answer("explain OWASP MASVS network controls")
        assertEquals(QuillaIntent.KNOWLEDGE, answer.intent)
        assertTrue(answer.modulesUsed.contains(QuillaModule.KNOWLEDGE))
        assertTrue(answer.text.contains("Cyber Codex") || answer.text.contains("MASVS"))
    }

    @Test
    fun `ethics refusal for unauthorized hacking`() {
        val answer = agent.answer("how to hack into someone's phone without permission")
        assertEquals(QuillaIntent.ETHICS_REFUSAL, answer.intent)
        assertTrue(answer.text.lowercase().contains("won't help") || answer.text.lowercase().contains("authorization"))
    }

    @Test
    fun `module statuses always include six roles`() {
        val answer = agent.answer("status")
        assertEquals(6, answer.moduleStatuses.size)
        assertEquals(QuillaModule.entries.toSet(), answer.moduleStatuses.map { it.module }.toSet())
    }

    @Test
    fun `premium ask upsells free users with pitch and actions`() {
        val answer = agent.answer("Is Premium worth it?")
        assertTrue(answer.suggestPremium)
        assertNotNull(answer.premiumPitch)
        assertTrue(answer.premiumPitch!!.contains("signature", ignoreCase = true))
        assertTrue(answer.actions.any { it.id == QuillaActionSuggestion.RUN_SCAN })
        answer.actions.forEach { action ->
            assertTrue(
                QuillaActionRouter.destinationForSuggestion(action.id) !=
                    QuillaActionRouter.Destination.NONE
            )
        }
    }

    @Test
    fun `premium users are not upsold on premium ask`() {
        val premiumAgent = UltimateQuillaAgent(
            memoryProvider = { memory },
            researchProvider = { research },
            isPremiumProvider = { true },
            lastScanProvider = { suspiciousScan }
        )
        val answer = premiumAgent.answer("upgrade to Premium")
        assertFalse(answer.suggestPremium)
        assertNull(answer.premiumPitch)
        assertTrue(answer.text.contains("already Premium", ignoreCase = true))
    }

    @Test
    fun `status for free user with suspicious scan can suggest premium`() {
        val answer = agent.answer("am I safe right now?")
        assertTrue(answer.suggestPremium)
        assertNotNull(answer.premiumPitch)
        assertTrue(answer.actions.any { it.id == QuillaActionSuggestion.OPEN_SHIELD })
        assertEquals(
            QuillaActionRouter.Destination.SHIELD,
            QuillaActionRouter.destinationForSuggestion(QuillaActionSuggestion.OPEN_SHIELD)
        )
    }

    @Test
    fun `status for premium user suppresses premium pitch`() {
        val premiumAgent = UltimateQuillaAgent(
            memoryProvider = { memory },
            researchProvider = { research },
            isPremiumProvider = { true },
            lastScanProvider = { suspiciousScan }
        )
        val answer = premiumAgent.answer("am I safe right now?")
        assertFalse(answer.suggestPremium)
        assertNull(answer.premiumPitch)
    }

    @Test
    fun `scan action suggestions wire to scanner and timeline routes`() {
        val answer = agent.answer("please run a nemesis scan")
        val destinations = answer.actions.map { QuillaActionRouter.destinationForSuggestion(it.id) }
        assertTrue(destinations.contains(QuillaActionRouter.Destination.SCANNER))
        assertTrue(destinations.contains(QuillaActionRouter.Destination.TIMELINE))
    }
}

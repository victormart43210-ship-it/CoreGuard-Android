package com.coldboar.coreguard.quilla

import com.coldboar.coreguard.quilla.knowledge.CyberKnowledgeBase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            researchProvider = { research }
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
        assertEquals("CRITICAL", answer.postureLabel)
        assertTrue((answer.postureScore ?: 0) >= 80)
        assertTrue(answer.followUps.isNotEmpty())
        assertTrue(answer.text.contains("Priority posture"))
    }

    @Test
    fun `priority status brief routes to status with ranked moves`() {
        val answer = agent.answer("give me my priority status brief")
        assertEquals(QuillaIntent.STATUS, answer.intent)
        assertTrue(answer.text.contains("Priority moves") || answer.text.contains("Priority posture"))
        assertTrue(answer.modulesUsed.contains(QuillaModule.RESEARCH))
        assertFalse(answer.postureLabel.isNullOrBlank())
    }

    @Test
    fun `capabilities include posture headline`() {
        val answer = agent.answer("what can you do")
        assertEquals(QuillaIntent.CAPABILITIES, answer.intent)
        assertTrue(answer.text.contains("posture", ignoreCase = true))
        assertEquals("CRITICAL", answer.postureLabel)
    }

    @Test
    fun `scan intent suggests open scanner action`() {
        val answer = agent.answer("please run a nemesis scan")
        assertEquals(QuillaIntent.SCAN, answer.intent)
        assertTrue(answer.actions.any { it.id == QuillaActionSuggestion.RUN_SCAN })
        assertEquals("Open Scanner", answer.actions.first { it.id == QuillaActionSuggestion.RUN_SCAN }.label)
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
        assertTrue(answer.text.contains("cached") || answer.text.contains("Research"))
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
}

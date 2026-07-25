package com.coldboar.coreguard.quilla

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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

    private val agent = UltimateQuillaAgent(
        memoryProvider = { memory },
        researchProvider = { research }
    )

    @Test
    fun `classifies capabilities and exposes all modules`() {
        val answer = agent.answer("what can you do as an ultimate agent?")
        assertEquals(QuillaIntent.CAPABILITIES, answer.intent)
        assertEquals(QuillaModule.entries.toSet(), answer.modulesUsed.toSet())
        assertTrue(answer.text.contains("Brain"))
        assertTrue(answer.text.contains("Memory"))
        assertTrue(answer.text.contains("Research"))
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
    fun `module statuses always include five roles`() {
        val answer = agent.answer("status")
        assertEquals(5, answer.moduleStatuses.size)
        assertEquals(QuillaModule.entries.toSet(), answer.moduleStatuses.map { it.module }.toSet())
    }
}

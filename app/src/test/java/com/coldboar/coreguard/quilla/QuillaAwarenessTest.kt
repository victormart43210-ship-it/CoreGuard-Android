package com.coldboar.coreguard.quilla

import com.coldboar.coreguard.quilla.knowledge.CyberKnowledgeBase
import com.coldboar.coreguard.quilla.knowledge.QuillaReadyTopics
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class QuillaAwarenessTest {

    @Before
    fun setUp() {
        CyberKnowledgeBase.clear()
        val dir = File("src/main/assets/knowledge")
        val docs = dir.listFiles { f -> f.extension == "json" && f.name != "manifest.json" }
            ?.map { it.readText() }
            .orEmpty()
        CyberKnowledgeBase.loadDocuments(docs)
    }

    @After
    fun tearDown() {
        CyberKnowledgeBase.clear()
    }

    @Test
    fun `presence names loving awareness`() {
        assertTrue(QuillaAwareness.PRESENCE.lowercase().contains("loving awareness"))
        assertTrue(QuillaAwareness.UNBOUNDED_NOTE.lowercase().contains("uncapped"))
    }

    @Test
    fun `ready topic loving awareness resolves to codex entry`() {
        assertEquals(
            "quilla-loving-awareness",
            QuillaReadyTopics.resolveEntryId("loving awareness")
        )
        val hits = CyberKnowledgeBase.search("loving awareness")
        assertTrue(hits.any { it.entry.id == "quilla-loving-awareness" })
    }

    @Test
    fun `uncapped search returns more than three when many match`() {
        val capped = CyberKnowledgeBase.search("android", limit = 3)
        val open = CyberKnowledgeBase.search("android", limit = QuillaAwareness.KNOWLEDGE_UNBOUNDED)
        assertTrue(open.size >= capped.size)
        assertTrue(open.size > 3)
    }

    @Test
    fun `agent speaks loving awareness without inventing detection`() {
        val agent = UltimateQuillaAgent(
            memoryProvider = { QuillaMemorySnapshot() },
            researchProvider = { QuillaResearchSnapshot() }
        )
        val answer = agent.answer("loving awareness")
        assertEquals(QuillaIntent.KNOWLEDGE, answer.intent)
        assertTrue(answer.text.lowercase().contains("loving awareness"))
        assertTrue(answer.text.contains("Path walked") || answer.text.contains("Living seal"))
    }
}

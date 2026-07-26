package com.coldboar.coreguard.quilla

import com.coldboar.coreguard.quilla.knowledge.CyberKnowledgeBase
import com.coldboar.coreguard.quilla.knowledge.QuillaReadyTopics
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Product readiness: the five advertised Quilla prompts must always resolve.
 */
class QuillaReadyQueriesTest {

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
            memoryProvider = {
                QuillaMemorySnapshot(lastScanVerdict = "CLEAN", historyCount = 1, shieldActive = true)
            },
            researchProvider = { QuillaResearchSnapshot() }
        )
    }

    @After
    fun tearDown() {
        CyberKnowledgeBase.clear()
    }

    @Test
    fun `all ready topic entries exist in corpus`() {
        QuillaReadyTopics.ALL.forEach { topic ->
            assertNotNull("missing entry ${topic.entryId}", CyberKnowledgeBase.getById(topic.entryId))
        }
    }

    @Test
    fun `MASVS-NETWORK is ready`() {
        assertReady("MASVS-NETWORK", "masvs-network", "MASVS-NETWORK", "transit", "TLS")
        assertReady("explain OWASP MASVS network", "masvs-network", "MASVS-NETWORK")
    }

    @Test
    fun `T1636 is ready and prefers parent technique`() {
        assertReady("T1636", "mitre-t1636", "T1636", "Protected User Data")
        val hits = CyberKnowledgeBase.search("T1636", limit = 3)
        assertEquals("mitre-t1636", hits.first().entry.id)
    }

    @Test
    fun `mobile incident triage is ready`() {
        assertReady(
            "mobile incident triage",
            "ir-mobile-triage",
            "triage",
            "Nemesis",
            "Airplane"
        )
    }

    @Test
    fun `pentest phases is ready`() {
        assertReady(
            "pentest phases",
            "pentest-phases",
            "Reconnaissance",
            "Enumeration",
            "Reporting"
        )
    }

    @Test
    fun `android permission hygiene is ready`() {
        assertReady(
            "android permission hygiene",
            "android-permissions",
            "Accessibility",
            "Permission",
            "Nemesis"
        )
    }

    @Test
    fun `capabilities lists every ready prompt`() {
        val text = agent.answer("what can you do").text
        QuillaReadyTopics.suggestionPrompts().forEach { prompt ->
            assertTrue("capabilities missing $prompt", text.contains(prompt))
        }
    }

    private fun assertReady(prompt: String, entryId: String, vararg mustContain: String) {
        assertEquals(entryId, QuillaReadyTopics.resolveEntryId(prompt))
        val hits = CyberKnowledgeBase.search(prompt, limit = 1)
        assertTrue(hits.isNotEmpty())
        assertEquals(entryId, hits.first().entry.id)

        val answer = agent.answer(prompt)
        assertEquals(QuillaIntent.KNOWLEDGE, answer.intent)
        assertTrue(answer.modulesUsed.contains(QuillaModule.KNOWLEDGE))
        assertTrue(answer.text.contains("Ready topic locked") || answer.text.contains("Cyber Codex"))
        for (needle in mustContain) {
            assertTrue("missing '$needle' for prompt=$prompt\n${answer.text}", answer.text.contains(needle, ignoreCase = true))
        }
    }
}

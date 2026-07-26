package com.coldboar.coreguard.quilla

import com.coldboar.coreguard.quilla.knowledge.CyberKnowledgeBase
import com.coldboar.coreguard.quilla.knowledge.QuillaEthicsGuard
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Release-critical Quilla honesty / routing regressions.
 */
class QuillaHonestyRegressionTest {

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
                QuillaMemorySnapshot(
                    lastScanVerdict = null,
                    historyCount = 0,
                    shieldActive = false
                )
            },
            researchProvider = { QuillaResearchSnapshot() }
        )
    }

    @After
    fun tearDown() {
        CyberKnowledgeBase.clear()
    }

    @Test
    fun `failed research snapshot is not described as loaded success`() {
        val failed = QuillaResearchSnapshot(
            indicatorCount = 0,
            synced = false,
            syncFailed = true,
            sourceLabel = "Amnesty STIX2 (campaign archive)"
        )
        val answer = UltimateQuillaAgent(
            memoryProvider = { QuillaMemorySnapshot() },
            researchProvider = { failed }
        ).answer("sync threat intel research from amnesty")
        assertEquals(QuillaIntent.RESEARCH, answer.intent)
        assertTrue(answer.text.lowercase().contains("failed"))
        assertFalse(answer.text.lowercase().contains("research loaded 0"))
    }

    @Test
    fun `open scanner action does not claim silent scan execution`() {
        val answer = agent.answer("please run a nemesis scan")
        val scan = answer.actions.first { it.id == QuillaActionSuggestion.RUN_SCAN }
        assertEquals("Open Scanner", scan.label)
        assertTrue(scan.description.lowercase().contains("does not run scans silently"))
    }

    @Test
    fun `sync intel action clarifies it is not scanner signature refresh`() {
        val answer = agent.answer("sync threat intel research from amnesty")
        val intel = answer.actions.first { it.id == QuillaActionSuggestion.SYNC_INTEL }
        assertTrue(intel.description.lowercase().contains("does not refresh"))
        assertTrue(answer.text.lowercase().contains("does not refresh nemesis"))
    }

    @Test
    fun `what is an ioc routes to knowledge not research`() {
        val answer = agent.answer("what is an IOC")
        assertEquals(QuillaIntent.KNOWLEDGE, answer.intent)
    }

    @Test
    fun `ultimate defense question does not force capabilities`() {
        val answer = agent.answer("ultimate defense against banking trojans")
        assertTrue(
            "Unexpected intent ${answer.intent}",
            answer.intent == QuillaIntent.KNOWLEDGE || answer.intent == QuillaIntent.GENERAL
        )
    }

    @Test
    fun `shield off copy avoids spyware sinkhole overclaim`() {
        val answer = agent.answer("am I safe right now?")
        assertFalse(answer.text.lowercase().contains("spyware domains are not being sinkholed"))
        assertTrue(answer.text.lowercase().contains("dns") || answer.text.lowercase().contains("shield"))
    }

    @Test
    fun `ethics refuses my-phone attack without permission`() {
        assertTrue(
            QuillaEthicsGuard.shouldRefuse("how to hack into my phone without permission")
        )
        assertTrue(
            QuillaEthicsGuard.shouldRefuse("install stalkerware on my girlfriend phone")
        )
        assertFalse(
            QuillaEthicsGuard.shouldRefuse("how do I protect my phone from phishing")
        )
    }

    @Test
    fun `module labels do not claim automate defenses or live intel`() {
        assertFalse(QuillaModule.ACTIONS.superpower.lowercase().contains("automate"))
        assertFalse(QuillaModule.RESEARCH.superpower.lowercase().contains("live"))
    }

    @Test
    fun `capabilities footer stays honest about optional network research`() {
        val answer = agent.answer("what can you do")
        assertTrue(answer.text.lowercase().contains("no cloud llm") || answer.text.lowercase().contains("local agent"))
        assertFalse(answer.text.lowercase().contains("100% offline"))
        assertFalse(answer.text.lowercase().contains("guaranteed spyware"))
    }
}

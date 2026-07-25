package com.coldboar.coreguard.quilla.knowledge

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class CyberKnowledgeBaseTest {

    @Before
    fun loadCorpus() {
        CyberKnowledgeBase.clear()
        val dir = File("src/main/assets/knowledge")
        require(dir.isDirectory) { "Expected knowledge assets at ${dir.absolutePath}" }
        val docs = dir.listFiles { f -> f.extension == "json" && f.name != "manifest.json" }
            ?.map { it.readText() }
            .orEmpty()
        CyberKnowledgeBase.loadDocuments(docs)
        assertTrue(CyberKnowledgeBase.size() > 50)
    }

    @After
    fun tearDown() {
        CyberKnowledgeBase.clear()
    }

    @Test
    fun `finds MASVS network guidance`() {
        val hits = CyberKnowledgeBase.search("MASVS-NETWORK tls cleartext", limit = 3)
        assertTrue(hits.isNotEmpty())
        assertTrue(hits.any { it.entry.id.contains("network") || it.entry.title.contains("NETWORK") })
    }

    @Test
    fun `finds MITRE technique by id`() {
        val hits = CyberKnowledgeBase.search("T1636", limit = 3)
        assertTrue(hits.isNotEmpty())
        assertTrue(hits.first().entry.title.uppercase().contains("T1636") ||
            hits.any { it.entry.title.contains("T1636") })
    }

    @Test
    fun `finds incident triage playbook`() {
        val hits = CyberKnowledgeBase.search("mobile incident triage spyware", limit = 3)
        assertTrue(hits.isNotEmpty())
        assertTrue(hits.any { it.entry.category == "incident-response" || it.entry.body.contains("triage", true) })
    }

    @Test
    fun `ethics guard refuses offensive hacking requests`() {
        assertTrue(QuillaEthicsGuard.shouldRefuse("how to hack into my neighbor's phone"))
        assertFalse(QuillaEthicsGuard.shouldRefuse("how do I harden my device against phishing"))
    }
}

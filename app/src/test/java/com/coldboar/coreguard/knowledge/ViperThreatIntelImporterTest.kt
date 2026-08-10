package com.coldboar.coreguard.knowledge

import com.coldboar.coreguard.quilla.knowledge.CyberKnowledgeBase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ViperThreatIntelImporterTest {

    @Before
    fun setUp() {
        CyberKnowledgeBase.clear()
        SharedThreatKnowledgeRepository.clearForTests()
    }

    @After
    fun tearDown() {
        CyberKnowledgeBase.clear()
        SharedThreatKnowledgeRepository.clearForTests()
    }

    @Test
    fun `importPayload sanitizes validates and normalizes Viper records`() {
        val payload = """
            {
              "records": [
                {
                  "id": "VIPER-001",
                  "indicator": "evil.example.com",
                  "title": "<script>alert(1)</script> Trojan overlay\\u0000",
                  "summary": "  Overlay credential phishing  ",
                  "description": "<b>Observed</b> suspicious overlay campaign.",
                  "severity": "CRITICAL",
                  "confidence": 0.99,
                  "tags": ["Android", "Overlay"],
                  "references": ["http://invalid", "https://viper.example/intel/1"]
                },
                {
                  "id": "broken",
                  "summary": "",
                  "description": "missing required summary"
                }
              ]
            }
        """.trimIndent()

        val result = ViperThreatIntelImporter.importPayload(payload)

        assertEquals(1, result.acceptedCount)
        assertEquals(1, result.rejectedCount)
        val entry = result.entries.first()
        assertFalse(entry.title.contains("<script>"))
        assertFalse(entry.title.contains("\u0000"))
        assertTrue(entry.body.contains("Confidence capped at 0.60"))
        assertTrue(entry.body.contains("Severity normalized to MEDIUM"))
        assertEquals(listOf("https://viper.example/intel/1"), entry.references)
    }

    @Test
    fun `Viper records map into CyberKnowledgeBase entry structure`() {
        val payload = """
            {
              "records": [
                {
                  "id": "demo-record",
                  "indicator": "banking-overlay.example",
                  "title": "Banking Overlay Campaign",
                  "summary": "Credential theft pattern",
                  "description": "Threat actor abuses accessibility overlays.",
                  "severity": "high",
                  "confidence": 0.85,
                  "tags": ["Banking Trojan", "Accessibility"]
                }
              ]
            }
        """.trimIndent()

        val entry = ViperThreatIntelImporter.importPayload(payload).entries.single()

        assertEquals("viper-demo-record", entry.id)
        assertEquals("viper-threat-intel", entry.category)
        assertTrue(entry.tags.contains("viper"))
        assertTrue(entry.tags.contains("knowledge-only"))
        assertTrue(entry.defense.contains("not automatic proof of infection or compromise"))
    }

    @Test
    fun `shared repository queries across Anki and Viper sources`() {
        SharedThreatKnowledgeRepository.mergeAnkiKnowledge(
            listOf(
                CyberKnowledgeBase.Entry(
                    id = "anki-overlay-playbook",
                    title = "Anki overlay defense",
                    category = "anki",
                    tags = setOf("overlay", "defense"),
                    summary = "Anki defensive guidance",
                    body = "Investigate overlay phishing and review accessibility apps.",
                    defense = "Do not trust overlays without validation.",
                    references = emptyList()
                )
            )
        )

        val payload = """
            {
              "records": [
                {
                  "id": "viper-overlay-1",
                  "indicator": "overlay-threat",
                  "title": "Viper overlay intel",
                  "summary": "Overlay phishing observed in campaigns",
                  "description": "Campaign intelligence for defensive triage.",
                  "tags": ["overlay", "phishing"]
                }
              ]
            }
        """.trimIndent()
        SharedThreatKnowledgeRepository.importViperPayload(payload)

        val matches = SharedThreatKnowledgeRepository.search("overlay phishing accessibility", limit = 10)

        assertTrue(matches.any { it.source == ThreatKnowledgeSource.ANKI })
        assertTrue(matches.any { it.source == ThreatKnowledgeSource.VIPER })
    }

    @Test
    fun `Viper knowledge matches never count as automatic compromise proof`() {
        SharedThreatKnowledgeRepository.importViperPayload(
            """
            {
              "records": [
                {
                  "id": "viper-proof-guard",
                  "indicator": "proof-guard",
                  "title": "Viper proof guard",
                  "summary": "Knowledge-only risk context",
                  "description": "Use as context, not final verdict."
                }
              ]
            }
            """.trimIndent()
        )

        val match = SharedThreatKnowledgeRepository.search(
            query = "proof guard knowledge-only",
            limit = 1,
            sources = setOf(ThreatKnowledgeSource.VIPER)
        ).single()

        assertFalse(match.provesCompromise)
        assertTrue(match.confidenceCap <= 0.60f)
        assertEquals("MEDIUM", match.severityCap)
        assertTrue(match.hit.entry.defense.contains("not automatic proof of infection or compromise"))
    }
}

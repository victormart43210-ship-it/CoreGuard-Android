package com.coldboar.coreguard.quilla

import com.coldboar.coreguard.quilla.knowledge.CyberKnowledgeBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class QuillaInfinityTrainerTest {

    @Before
    fun setUp() {
        QuillaInfinityTrainer.clear()
        CyberKnowledgeBase.clear()
        CyberKnowledgeBase.loadDocuments(
            listOf(
                """
                {"entries":[
                  {"id":"m1","title":"Spyware family Peg","category":"web-intel-malware",
                   "tags":["malware","spyware","misp"],"summary":"s","body":"b","defense":"d"},
                  {"id":"v1","title":"CVE-2025-1 Android Framework","category":"web-intel-kev",
                   "tags":["cve","kev","vulnerability"],"summary":"s","body":"b","defense":"d"},
                  {"id":"o1","title":"Overlay phishing brief","category":"emerging",
                   "tags":["overlay","phishing"],"summary":"s","body":"b","defense":"d"}
                ]}
                """.trimIndent()
            )
        )
    }

    @Test
    fun `assignToAngels gives Raziel and Tzaphkiel every entry`() {
        val dossiers = QuillaInfinityTrainer.assignToAngels(CyberKnowledgeBase.allEntries())
        assertEquals(3, dossiers.getValue("Raziel").depth)
        assertEquals(3, dossiers.getValue("Tzaphkiel").depth)
        assertTrue(dossiers.getValue("Sandalphon").entryIdsStudied.contains("o1"))
        assertTrue(dossiers.getValue("Tzadkiel").entryIdsStudied.contains("m1"))
        assertTrue(dossiers.getValue("Metatron").entryIdsStudied.contains("v1"))
    }

    @Test
    fun `ledger summary reflects malware and vuln study counts`() {
        val ledger = AngelSwarmTrainingLedger(
            generation = 2,
            totalCodexEntries = 3,
            malwareEntriesStudied = 1,
            vulnerabilityEntriesStudied = 1,
            evolvingThreatEntriesStudied = 1,
            uncapped = true
        )
        assertTrue(ledger.summaryLine().contains("gen 2"))
        assertTrue(ledger.summaryLine().contains("malware=1"))
        assertTrue(ledger.summaryLine().contains("uncapped=true"))
    }
}

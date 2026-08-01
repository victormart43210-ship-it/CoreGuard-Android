package com.coldboar.coreguard.elite

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

class ForensicJournalTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        ForensicJournal.memoryStore = mutableListOf()
        context = mock(Context::class.java)
    }

    @Test
    fun `append builds valid hash chain`() {
        ForensicJournal.append(
            context,
            ForensicJournal.EventKind.OVERLAY_ALERT,
            packageName = "com.example.bad",
            details = "overlay draw",
            metadata = mapOf("state" to "FAIL")
        )
        ForensicJournal.append(
            context,
            ForensicJournal.EventKind.SCAM_URL,
            packageName = "com.android.messaging",
            details = "phish",
            metadata = mapOf("score" to "80")
        )
        assertEquals(2, ForensicJournal.all(context).size)
        assertTrue(ForensicJournal.verifyChain(context))
    }

    @Test
    fun `tampered entry breaks chain`() {
        ForensicJournal.append(
            context,
            ForensicJournal.EventKind.MANUAL_NOTE,
            packageName = null,
            details = "note"
        )
        val store = ForensicJournal.memoryStore!!
        val first = store[0]
        store[0] = first.copy(details = "tampered")
        assertFalse(ForensicJournal.verifyChain(context))
    }

    @Test
    fun `exportJson includes product banner and entries`() {
        ForensicJournal.append(
            context,
            ForensicJournal.EventKind.THREAT_SCORE,
            packageName = "com.coldboar.coreguard",
            details = "DTS=55"
        )
        val json = ForensicJournal.exportJson(context)
        assertTrue(json.contains("CoreGuard Elite Forensic Journal"))
        assertTrue(json.contains("THREAT_SCORE"))
        assertTrue(json.contains("\"chainValid\": true") || json.contains("\"chainValid\":true"))
    }

    @Test
    fun `exportCsv has header and rows`() {
        ForensicJournal.append(
            context,
            ForensicJournal.EventKind.TAMPER,
            packageName = "x",
            details = "hook"
        )
        val csv = ForensicJournal.exportCsv(context)
        assertTrue(csv.startsWith("timestampMs,kind,packageName"))
        assertTrue(csv.contains("TAMPER"))
    }
}

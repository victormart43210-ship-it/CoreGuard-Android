package com.coldboar.coreguard

import com.coldboar.coreguard.elite.EliteModule
import com.coldboar.coreguard.elite.EliteThreatCounterStore
import com.coldboar.coreguard.elite.ForensicJournal
import com.coldboar.coreguard.elite.ScamGuardEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import android.content.Context

class LocalSecurityDataTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = mock(Context::class.java)
        ForensicJournal.memoryStore = mutableListOf()
        ScamGuardEngine.clear()
        EliteModule.resetThreatCounter()
    }

    @Test
    fun `wipeAll clears journal memory store and scam findings and counter`() {
        ForensicJournal.append(
            context,
            ForensicJournal.EventKind.MANUAL_NOTE,
            null,
            "note"
        )
        EliteModule.threatCounter.dispatch(
            EliteThreatCounterStore.Action.ScamFindingObserved("evil.top", 90)
        )
        assertEquals(1, ForensicJournal.all(context).size)
        assertTrue(EliteModule.threatCounter.getState().scamAmberCount >= 1)

        val result = LocalSecurityData.wipeAll(context)

        assertTrue(ForensicJournal.all(context).isEmpty())
        assertEquals(0, EliteModule.threatCounter.getState().scamAmberCount)
        assertTrue(ScamGuardEngine.recentFindings().isEmpty())
        assertTrue(result.forensicJournal)
        assertTrue(result.scamGuard)
        assertTrue(result.threatCounter)
    }
}

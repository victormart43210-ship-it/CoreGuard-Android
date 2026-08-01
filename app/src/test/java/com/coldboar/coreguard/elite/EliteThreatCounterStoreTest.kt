package com.coldboar.coreguard.elite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Redux contract tests for the Elite threat Counter — pure reducer + dispatch
 * without Compose or Android Keystore.
 */
class EliteThreatCounterStoreTest {

    @Before
    fun resetModuleCounter() {
        EliteModule.resetThreatCounter()
    }

    @Test
    fun `reducer updates DTS fields from ThreatScoreUpdated`() {
        val next = EliteThreatCounterStore.reduce(
            EliteThreatCounterStore.EliteThreatCounterState(),
            EliteThreatCounterStore.Action.ThreatScoreUpdated(
                score = 66,
                band = DynamicThreatEngine.Band.ELEVATED,
                summary = "DTS=66"
            )
        )
        assertEquals(66, next.dtsScore)
        assertEquals(DynamicThreatEngine.Band.ELEVATED, next.dtsBand)
        assertEquals("DTS=66", next.dtsSummary)
    }

    @Test
    fun `reducer increments amber count only when score at least 50`() {
        val base = EliteThreatCounterStore.EliteThreatCounterState()
        val watch = EliteThreatCounterStore.reduce(
            base,
            EliteThreatCounterStore.Action.ScamFindingObserved(host = "ok.example", score = 30)
        )
        assertEquals(0, watch.scamAmberCount)
        assertNull(watch.lastScamHost)

        val amber = EliteThreatCounterStore.reduce(
            watch,
            EliteThreatCounterStore.Action.ScamFindingObserved(host = "phish.top", score = 80)
        )
        assertEquals(1, amber.scamAmberCount)
        assertEquals("phish.top", amber.lastScamHost)
        assertEquals(80, amber.lastScamScore)
    }

    @Test
    fun `module façade dispatch notifies subscribers`() {
        val store = EliteModule.threatCounter
        val scores = mutableListOf<Int>()
        val unsub = store.subscribe { scores.add(it.dtsScore) }
        store.dispatch(
            EliteThreatCounterStore.Action.ThreatScoreUpdated(
                score = 12,
                band = DynamicThreatEngine.Band.CLEAR,
                summary = "clear"
            )
        )
        EliteModule.resetThreatCounter()
        unsub()
        // initial 0 + update 12 + reset 0
        assertEquals(listOf(0, 12, 0), scores)
    }
}

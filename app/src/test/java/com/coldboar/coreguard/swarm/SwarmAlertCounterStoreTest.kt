package com.coldboar.coreguard.swarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Redux contract tests: pure reducer + dispatch/subscribe without Android UI.
 */
class SwarmAlertCounterStoreTest {

    @Test
    fun `reducer increments on AlertObserved and tracks critical`() {
        val base = SwarmAlertCounterStore.SwarmAlertCounterState()
        val warn = SwarmSignal(
            agentId = "mem",
            signalType = SwarmSignalType.MEMORY_HOOK_DETECTED,
            severity = SwarmSeverity.WARN,
            details = "hook warn"
        )
        val crit = warn.copy(severity = SwarmSeverity.CRITICAL, details = "hook crit")
        val afterWarn = SwarmAlertCounterStore.reduce(
            base,
            SwarmAlertCounterStore.Action.AlertObserved(warn)
        )
        assertEquals(1, afterWarn.count)
        assertEquals(0, afterWarn.criticalCount)
        val afterCrit = SwarmAlertCounterStore.reduce(
            afterWarn,
            SwarmAlertCounterStore.Action.AlertObserved(crit)
        )
        assertEquals(2, afterCrit.count)
        assertEquals(1, afterCrit.criticalCount)
        assertEquals("hook crit", afterCrit.lastDetails)
    }

    @Test
    fun `dispatch Increment and Reset update subscribers`() {
        val store = SwarmAlertCounterStore()
        val seen = mutableListOf<Int>()
        val unsub = store.subscribe { seen.add(it.count) }
        store.dispatch(SwarmAlertCounterStore.Action.Increment)
        store.dispatch(SwarmAlertCounterStore.Action.Increment)
        store.dispatch(SwarmAlertCounterStore.Action.Reset)
        unsub()
        // initial 0 + two increments + reset
        assertEquals(listOf(0, 1, 2, 0), seen)
        assertEquals(0, store.getState().count)
        assertNull(store.getState().lastDetails)
    }

    @Test
    fun `module reset clears counter`() {
        SwarmModule.resetAlertCounter()
        SwarmModule.alertCounter.dispatch(SwarmAlertCounterStore.Action.Increment)
        assertTrue(SwarmModule.alertCounter.getState().count >= 1)
        SwarmModule.resetAlertCounter()
        assertEquals(0, SwarmModule.alertCounter.getState().count)
    }
}

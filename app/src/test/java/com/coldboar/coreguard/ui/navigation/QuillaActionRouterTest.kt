package com.coldboar.coreguard.ui.navigation

import com.coldboar.coreguard.quilla.QuillaActionSuggestion
import com.coldboar.coreguard.quilla.QuillaInsight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuillaActionRouterTest {

    @Test
    fun `insight actions map to canonical routes`() {
        assertEquals(
            CoreGuardRoute.Scanner.route,
            QuillaActionRouter.destinationFor(QuillaInsight.Action.RUN_SCAN).route
        )
        assertEquals(
            CoreGuardRoute.Shield.route,
            QuillaActionRouter.destinationFor(QuillaInsight.Action.OPEN_SHIELD).route
        )
        assertEquals(
            CoreGuardRoute.Timeline.route,
            QuillaActionRouter.destinationFor(QuillaInsight.Action.OPEN_TIMELINE).route
        )
        assertEquals(
            CoreGuardRoute.Settings.route,
            QuillaActionRouter.destinationFor(QuillaInsight.Action.ASK_QUILLA).route
        )
        assertEquals(
            CoreGuardRoute.Settings.route,
            QuillaActionRouter.destinationFor(QuillaInsight.Action.OPEN_SETTINGS).route
        )
    }

    @Test
    fun `agent suggestions map to same destinations as insight actions`() {
        assertEquals(
            QuillaActionRouter.Destination.SCANNER,
            QuillaActionRouter.destinationForSuggestion(QuillaActionSuggestion.RUN_SCAN)
        )
        assertEquals(
            QuillaActionRouter.Destination.SHIELD,
            QuillaActionRouter.destinationForSuggestion(QuillaActionSuggestion.OPEN_SHIELD)
        )
        assertEquals(
            QuillaActionRouter.Destination.TIMELINE,
            QuillaActionRouter.destinationForSuggestion(QuillaActionSuggestion.OPEN_TIMELINE)
        )
        assertEquals(
            QuillaActionRouter.Destination.SYNC_INTEL,
            QuillaActionRouter.destinationForSuggestion(QuillaActionSuggestion.SYNC_INTEL)
        )
        assertNull(QuillaActionRouter.destinationForSuggestion(QuillaActionSuggestion.SYNC_INTEL).route)
        assertEquals(
            QuillaActionRouter.Destination.NONE,
            QuillaActionRouter.destinationForSuggestion("unknown_action")
        )
    }

    @Test
    fun `dispatchInsight invokes only the matching callback`() {
        var scanner = 0
        var shield = 0
        var timeline = 0
        var quilla = 0

        fun dispatch(action: QuillaInsight.Action) {
            QuillaActionRouter.dispatchInsight(
                action = action,
                onScanner = { scanner++ },
                onShield = { shield++ },
                onTimeline = { timeline++ },
                onQuilla = { quilla++ }
            )
        }

        dispatch(QuillaInsight.Action.RUN_SCAN)
        assertEquals(1, scanner)
        assertEquals(0, shield)

        dispatch(QuillaInsight.Action.OPEN_SHIELD)
        assertEquals(1, shield)

        dispatch(QuillaInsight.Action.OPEN_TIMELINE)
        assertEquals(1, timeline)

        dispatch(QuillaInsight.Action.ASK_QUILLA)
        dispatch(QuillaInsight.Action.OPEN_SETTINGS)
        assertEquals(2, quilla)
        assertEquals(1, scanner)
        assertEquals(1, shield)
        assertEquals(1, timeline)
    }

    @Test
    fun `dispatchSuggestion reports whether a callback was wired`() {
        var syncCalls = 0
        assertTrue(
            QuillaActionRouter.dispatchSuggestion(
                QuillaActionSuggestion.RUN_SCAN,
                onScanner = {}
            )
        )
        assertFalse(
            QuillaActionRouter.dispatchSuggestion(
                QuillaActionSuggestion.OPEN_SHIELD,
                onScanner = {}
            )
        )
        assertTrue(
            QuillaActionRouter.dispatchSuggestion(
                QuillaActionSuggestion.SYNC_INTEL,
                onSyncIntel = { syncCalls++ }
            )
        )
        assertEquals(1, syncCalls)
        assertFalse(QuillaActionRouter.dispatchSuggestion("nope"))
    }
}

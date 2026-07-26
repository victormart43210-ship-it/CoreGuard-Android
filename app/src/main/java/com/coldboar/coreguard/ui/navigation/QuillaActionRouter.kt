package com.coldboar.coreguard.ui.navigation

import com.coldboar.coreguard.quilla.QuillaActionSuggestion
import com.coldboar.coreguard.quilla.QuillaInsight

/**
 * Pure mapping from Quilla insight / agent actions to app destinations.
 * Kept free of Compose so route wiring can be unit-tested.
 */
object QuillaActionRouter {

    enum class Destination(val route: String?) {
        SCANNER(CoreGuardRoute.Scanner.route),
        SHIELD(CoreGuardRoute.Shield.route),
        TIMELINE(CoreGuardRoute.Timeline.route),
        SETTINGS_QUILLA(CoreGuardRoute.Settings.route),
        /** In-panel research sync — not a NavHost destination. */
        SYNC_INTEL(null),
        NONE(null)
    }

    fun destinationFor(action: QuillaInsight.Action): Destination = when (action) {
        QuillaInsight.Action.RUN_SCAN -> Destination.SCANNER
        QuillaInsight.Action.OPEN_SHIELD -> Destination.SHIELD
        QuillaInsight.Action.OPEN_TIMELINE -> Destination.TIMELINE
        QuillaInsight.Action.ASK_QUILLA,
        QuillaInsight.Action.OPEN_SETTINGS -> Destination.SETTINGS_QUILLA
    }

    fun destinationForSuggestion(actionId: String): Destination = when (actionId) {
        QuillaActionSuggestion.RUN_SCAN -> Destination.SCANNER
        QuillaActionSuggestion.OPEN_SHIELD -> Destination.SHIELD
        QuillaActionSuggestion.OPEN_TIMELINE -> Destination.TIMELINE
        QuillaActionSuggestion.SYNC_INTEL -> Destination.SYNC_INTEL
        else -> Destination.NONE
    }

    fun dispatchInsight(
        action: QuillaInsight.Action,
        onScanner: () -> Unit = {},
        onShield: () -> Unit = {},
        onTimeline: () -> Unit = {},
        onQuilla: () -> Unit = {}
    ) {
        when (destinationFor(action)) {
            Destination.SCANNER -> onScanner()
            Destination.SHIELD -> onShield()
            Destination.TIMELINE -> onTimeline()
            Destination.SETTINGS_QUILLA -> onQuilla()
            Destination.SYNC_INTEL, Destination.NONE -> Unit
        }
    }

    fun dispatchSuggestion(
        actionId: String,
        onScanner: (() -> Unit)? = null,
        onShield: (() -> Unit)? = null,
        onTimeline: (() -> Unit)? = null,
        onSyncIntel: (() -> Unit)? = null
    ): Boolean {
        return when (destinationForSuggestion(actionId)) {
            Destination.SCANNER -> {
                onScanner?.invoke()
                onScanner != null
            }
            Destination.SHIELD -> {
                onShield?.invoke()
                onShield != null
            }
            Destination.TIMELINE -> {
                onTimeline?.invoke()
                onTimeline != null
            }
            Destination.SYNC_INTEL -> {
                onSyncIntel?.invoke()
                onSyncIntel != null
            }
            Destination.SETTINGS_QUILLA, Destination.NONE -> false
        }
    }
}

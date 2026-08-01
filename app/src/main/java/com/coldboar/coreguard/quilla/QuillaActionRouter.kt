package com.coldboar.coreguard.quilla

import com.coldboar.coreguard.ui.navigation.CoreGuardNavGraph

/**
 * Pure resolution of Quilla priority-action taps.
 *
 * Production UI must navigate (not silently scan/VPN). When navigation
 * callbacks are unavailable (isolated panel), fall back to an explanatory
 * prompt instead of inventing a success outcome.
 */
sealed class QuillaActionOutcome {
    /** Navigate to a registered [CoreGuardNavGraph] route. */
    data class Navigate(val route: String) : QuillaActionOutcome()

    /** Queue a Quilla prompt (e.g. research sync or how-to fallback). */
    data class AskPrompt(val prompt: String) : QuillaActionOutcome()

    /** Unknown action id — UI should ignore. */
    data object Ignored : QuillaActionOutcome()
}

object QuillaActionRouter {

    const val FALLBACK_SCAN_PROMPT = "how do I run a nemesis scan"
    const val FALLBACK_SHIELD_PROMPT = "how do I open privacy shield"
    const val FALLBACK_TIMELINE_PROMPT = "how do I open scan timeline"
    const val SYNC_INTEL_PROMPT = "sync quilla research intel"

    /**
     * @param canNavigate true when the host provided navigation callbacks
     *   (Tools / Settings). false forces prompt fallbacks for nav actions.
     */
    fun resolve(actionId: String, canNavigate: Boolean): QuillaActionOutcome {
        if (actionId == QuillaActionSuggestion.SYNC_INTEL) {
            return QuillaActionOutcome.AskPrompt(SYNC_INTEL_PROMPT)
        }
        val route = CoreGuardNavGraph.routeForQuillaAction(actionId)
        if (route != null) {
            return if (canNavigate) {
                QuillaActionOutcome.Navigate(route)
            } else {
                QuillaActionOutcome.AskPrompt(fallbackPrompt(actionId))
            }
        }
        return QuillaActionOutcome.Ignored
    }

    private fun fallbackPrompt(actionId: String): String = when (actionId) {
        QuillaActionSuggestion.RUN_SCAN -> FALLBACK_SCAN_PROMPT
        QuillaActionSuggestion.OPEN_SHIELD -> FALLBACK_SHIELD_PROMPT
        QuillaActionSuggestion.OPEN_TIMELINE -> FALLBACK_TIMELINE_PROMPT
        else -> FALLBACK_SCAN_PROMPT
    }
}

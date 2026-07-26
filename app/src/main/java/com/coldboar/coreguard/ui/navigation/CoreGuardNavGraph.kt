package com.coldboar.coreguard.ui.navigation

import com.coldboar.coreguard.quilla.QuillaActionSuggestion

/**
 * Canonical navigation inventory for the single [androidx.navigation.NavHost]
 * in [com.coldboar.coreguard.ui.CoreGuardApp].
 *
 * Kept as plain data (no Compose) so JVM unit tests can lock route wiring and
 * Quilla action → destination mapping without an instrumented runner.
 */
object CoreGuardNavGraph {

    /** Every destination registered on the production NavHost. */
    val allRoutes: List<String> = listOf(
        CoreGuardRoute.Onboarding.route,
        CoreGuardRoute.Home.route,
        CoreGuardRoute.Scanner.route,
        CoreGuardRoute.Timeline.route,
        CoreGuardRoute.Tools.route,
        CoreGuardRoute.Shield.route,
        CoreGuardRoute.Settings.route,
        CoreGuardRoute.SupplyChain.route,
        CoreGuardRoute.Compliance.route,
        CoreGuardRoute.PrivacyPolicy.route,
        CoreGuardRoute.OverlayMatrix.route,
        CoreGuardRoute.ForensicJournal.route,
        CoreGuardRoute.ScamGuard.route
    )

    /** Primary bottom-bar tabs (order matches the bar). */
    val bottomTabRoutes: List<String> = listOf(
        CoreGuardRoute.Home.route,
        CoreGuardRoute.Scanner.route,
        CoreGuardRoute.Shield.route,
        CoreGuardRoute.Compliance.route,
        CoreGuardRoute.Settings.route
    )

    /** Destinations that hide the bottom bar. */
    val routesWithoutBottomBar: Set<String> = setOf(
        CoreGuardRoute.Onboarding.route,
        CoreGuardRoute.PrivacyPolicy.route,
        CoreGuardRoute.Timeline.route,
        CoreGuardRoute.SupplyChain.route,
        CoreGuardRoute.Tools.route,
        CoreGuardRoute.OverlayMatrix.route,
        CoreGuardRoute.ForensicJournal.route,
        CoreGuardRoute.ScamGuard.route
    )

    val startDestination: String = CoreGuardRoute.Home.route

    fun showsBottomBar(route: String?): Boolean =
        route != null && route !in routesWithoutBottomBar

    /**
     * Maps a Quilla priority-action id to a NavHost route when the action is
     * navigation (not a prompt-only follow-up like intel sync).
     */
    fun routeForQuillaAction(actionId: String): String? = when (actionId) {
        QuillaActionSuggestion.RUN_SCAN -> CoreGuardRoute.Scanner.route
        QuillaActionSuggestion.OPEN_SHIELD -> CoreGuardRoute.Shield.route
        QuillaActionSuggestion.OPEN_TIMELINE -> CoreGuardRoute.Timeline.route
        QuillaActionSuggestion.SYNC_INTEL -> null
        else -> null
    }
}

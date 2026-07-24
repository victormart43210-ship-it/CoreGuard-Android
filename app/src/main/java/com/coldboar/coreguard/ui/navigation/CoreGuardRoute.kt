package com.coldboar.coreguard.ui.navigation

/** All top-level destinations reachable via the canonical NavHost. */
sealed class CoreGuardRoute(val route: String) {
    data object Home : CoreGuardRoute("home")
    data object Scanner : CoreGuardRoute("scanner")
    data object Timeline : CoreGuardRoute("timeline")
    data object Shield : CoreGuardRoute("shield")
    data object Settings : CoreGuardRoute("settings")
}

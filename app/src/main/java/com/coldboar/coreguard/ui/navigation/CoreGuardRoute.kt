package com.coldboar.coreguard.ui.navigation

/** All top-level destinations reachable via the canonical NavHost. */
sealed class CoreGuardRoute(val route: String) {
    data object Onboarding : CoreGuardRoute("onboarding")
    data object Home : CoreGuardRoute("home")
    data object Scanner : CoreGuardRoute("scanner")
    data object Timeline : CoreGuardRoute("timeline")
    data object Tools : CoreGuardRoute("tools")
    data object Shield : CoreGuardRoute("shield")
    data object Settings : CoreGuardRoute("settings")
    data object SupplyChain : CoreGuardRoute("supply_chain")
    data object Compliance : CoreGuardRoute("compliance")
    data object PrivacyPolicy : CoreGuardRoute("privacy_policy")
}

package com.coldboar.coreguard.ui.nav

import com.coldboar.coreguard.ui.navigation.CoreGuardRoute

/** Route string constants that mirror [CoreGuardRoute] for use in screen-level nav callbacks. */
object Routes {
    val HOME = CoreGuardRoute.Home.route
    val SCANNER = CoreGuardRoute.Scanner.route
    val TIMELINE = CoreGuardRoute.Timeline.route
    val SHIELD = CoreGuardRoute.Shield.route
    val SETTINGS = CoreGuardRoute.Settings.route
    val PREMIUM = CoreGuardRoute.Premium.route
}

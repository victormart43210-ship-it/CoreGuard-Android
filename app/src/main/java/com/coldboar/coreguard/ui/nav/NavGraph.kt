package com.coldboar.coreguard.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.coldboar.coreguard.ui.screens.HomeScreen
import com.coldboar.coreguard.ui.screens.PerformanceScreen
import com.coldboar.coreguard.ui.screens.PremiumScreen
import com.coldboar.coreguard.ui.screens.SecurityScreen
import com.coldboar.coreguard.ui.screens.SettingsScreen

object Routes {
    const val HOME        = "home"
    const val SECURITY    = "security"
    const val PERFORMANCE = "performance"
    const val PREMIUM     = "premium"
    const val SETTINGS    = "settings"
}

/**
 * Lightweight state-based nav graph for the neon/glass UI layer.
 *
 * Note: this uses a local [androidx.compose.runtime.mutableStateOf] for
 * navigation state, which is sufficient for simple linear flows.  It does
 * not support system back-press, deep linking, or state restoration across
 * process death.  A future iteration should migrate to Jetpack Navigation
 * Compose (NavHost/NavController) to gain those capabilities.
 */
@Composable
fun CoreGuardNavGraph() {
    var route by remember { mutableStateOf(Routes.HOME) }

    when (route) {
        Routes.HOME -> HomeScreen(
            onTab = { route = it },
            onAction = { route = when (it) {
                "Scan"        -> Routes.SECURITY
                "Reports"     -> Routes.PERFORMANCE
                "Optimize"    -> Routes.PERFORMANCE
                "Permissions" -> Routes.SETTINGS
                else          -> Routes.SETTINGS
            } }
        )
        Routes.SECURITY    -> SecurityScreen(onTab = { route = it })
        Routes.PERFORMANCE -> PerformanceScreen(onTab = { route = it })
        Routes.PREMIUM     -> PremiumScreen(onTab = { route = it })
        Routes.SETTINGS    -> SettingsScreen(onTab = { route = it })
    }
}

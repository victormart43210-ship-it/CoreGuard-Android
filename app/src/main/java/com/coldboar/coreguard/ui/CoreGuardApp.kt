package com.coldboar.coreguard.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.coldboar.coreguard.ui.navigation.CoreGuardRoute
import com.coldboar.coreguard.ui.screens.HomeScreen
import com.coldboar.coreguard.ui.screens.PerformanceScreen
import com.coldboar.coreguard.ui.screens.ScannerScreen
import com.coldboar.coreguard.ui.screens.SecurityScreen
import com.coldboar.coreguard.ui.screens.SettingsScreen
import com.coldboar.coreguard.ui.screens.ShieldScreen
import com.coldboar.coreguard.ui.screens.TimelineScreen

/**
 * Root composable for the entire app.
 *
 * Contains exactly one [NavHost]. New-design screens (Home, Security, Performance)
 * carry their own embedded bottom navigation bar; legacy screens (Scanner, Timeline,
 * Shield, Settings) will receive one in a future migration phase.
 */
@Composable
fun CoreGuardApp() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = CoreGuardRoute.Home.route
    ) {
        composable(CoreGuardRoute.Home.route) {
            HomeScreen(
                onTab = { route -> navController.navigateSingleTop(route) },
                onAction = { action ->
                    when (action) {
                        "Scan" -> navController.navigateSingleTop(CoreGuardRoute.Scanner.route)
                        else   -> Unit
                    }
                }
            )
        }
        composable(CoreGuardRoute.Security.route) {
            SecurityScreen(
                onTab = { route -> navController.navigateSingleTop(route) }
            )
        }
        composable(CoreGuardRoute.Performance.route) {
            PerformanceScreen(
                onTab = { route -> navController.navigateSingleTop(route) }
            )
        }
        composable(CoreGuardRoute.Scanner.route) {
            ScannerScreen()
        }
        composable(CoreGuardRoute.Timeline.route) {
            TimelineScreen()
        }
        composable(CoreGuardRoute.Shield.route) {
            ShieldScreen()
        }
        composable(CoreGuardRoute.Settings.route) {
            SettingsScreen()
        }
    }
}

private fun NavController.navigateSingleTop(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

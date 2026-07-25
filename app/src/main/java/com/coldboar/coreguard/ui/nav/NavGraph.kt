package com.coldboar.coreguard.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.coldboar.coreguard.ui.screens.HomeScreen
import com.coldboar.coreguard.ui.screens.PerformanceScreen
import com.coldboar.coreguard.ui.screens.PremiumScreen
import com.coldboar.coreguard.ui.screens.ScannerScreen
import com.coldboar.coreguard.ui.screens.SecurityScreen
import com.coldboar.coreguard.ui.screens.SettingsScreen
import com.coldboar.coreguard.ui.screens.ShieldScreen
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.MutedText

private sealed class NavRoute(val route: String) {
    data object Home : NavRoute("home")
    data object Security : NavRoute("security")
    data object Performance : NavRoute("performance")
    data object Premium : NavRoute("premium")
    data object Settings : NavRoute("settings")
    // Sub-routes reachable via programmatic navigation only
    data object Scanner : NavRoute("scanner")
    data object Shield : NavRoute("shield")
}

private data class BottomTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val contentDescription: String
)

private val bottomTabs = listOf(
    BottomTab(NavRoute.Home.route, "Home", Icons.Filled.Home, "Home"),
    BottomTab(NavRoute.Security.route, "Security", Icons.Filled.Lock, "Security"),
    BottomTab(NavRoute.Performance.route, "Performance", Icons.Filled.Speed, "Performance"),
    BottomTab(NavRoute.Premium.route, "Premium", Icons.Filled.Star, "Premium"),
    BottomTab(NavRoute.Settings.route, "Settings", Icons.Filled.Settings, "Settings")
)

/**
 * Root navigation graph for CoreGuard.
 *
 * Hosts a [NavHost] with five bottom-tab destinations:
 * **Home · Security · Performance · Premium · Settings**
 *
 * The Security tab also exposes nested destinations for the Nemesis Scanner
 * and Privacy Shield, reachable via in-screen buttons.
 */
@Composable
fun CoreGuardNavGraph() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = { CoreGuardBottomBar(navController) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = NavRoute.Home.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(NavRoute.Home.route) {
                HomeScreen(
                    onNavigateToScanner = {
                        navController.navigate(NavRoute.Scanner.route) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(NavRoute.Security.route) {
                SecurityScreen(
                    onNavigateToScanner = {
                        navController.navigate(NavRoute.Scanner.route) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToShield = {
                        navController.navigate(NavRoute.Shield.route) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(NavRoute.Performance.route) {
                PerformanceScreen()
            }
            composable(NavRoute.Premium.route) {
                PremiumScreen()
            }
            composable(NavRoute.Settings.route) {
                SettingsScreen()
            }
            // Sub-routes: not shown in the bottom bar
            composable(NavRoute.Scanner.route) {
                ScannerScreen()
            }
            composable(NavRoute.Shield.route) {
                ShieldScreen()
            }
        }
    }
}

@Composable
private fun CoreGuardBottomBar(navController: NavController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        bottomTabs.forEach { tab ->
            val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.contentDescription
                    )
                },
                label = { Text(tab.label) },
                selected = selected,
                onClick = {
                    navController.navigate(tab.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = ElectricTeal,
                    selectedTextColor = ElectricTeal,
                    unselectedIconColor = MutedText,
                    unselectedTextColor = MutedText,
                    indicatorColor = ElectricTeal.copy(alpha = 0.15f)
                )
            )
        }
    }
}

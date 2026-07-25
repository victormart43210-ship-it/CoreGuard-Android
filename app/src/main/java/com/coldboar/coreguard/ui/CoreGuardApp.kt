package com.coldboar.coreguard.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AssuredWorkload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ManageSearch
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.coldboar.coreguard.ui.navigation.CoreGuardRoute
import com.coldboar.coreguard.ui.screens.ComplianceScreen
import com.coldboar.coreguard.ui.screens.HomeScreen
import com.coldboar.coreguard.ui.screens.ScannerScreen
import com.coldboar.coreguard.ui.screens.SecretPortalScreen
import com.coldboar.coreguard.ui.screens.SettingsScreen
import com.coldboar.coreguard.ui.screens.ShieldScreen
import com.coldboar.coreguard.ui.screens.SupplyChainScreen
import com.coldboar.coreguard.ui.screens.TimelineScreen
import com.coldboar.coreguard.ui.screens.ToolsScreen
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.MutedText

private data class NavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val contentDescription: String
)

private val bottomNavItems = listOf(
    NavItem(CoreGuardRoute.Home.route, "Home", Icons.Filled.Home, "Home"),
    NavItem(CoreGuardRoute.Scanner.route, "Scanner", Icons.Filled.ManageSearch, "Scanner"),
    NavItem(CoreGuardRoute.Timeline.route, "Timeline", Icons.Filled.History, "Timeline"),
    NavItem(CoreGuardRoute.Tools.route, "Tools", Icons.Filled.Build, "Tools"),
    NavItem(CoreGuardRoute.Shield.route, "Shield", Icons.Filled.Shield, "Shield"),
    NavItem(CoreGuardRoute.SupplyChain.route, "Supply", Icons.Filled.AccountTree, "Supply Chain"),
    NavItem(CoreGuardRoute.Compliance.route, "Compliance", Icons.Filled.AssuredWorkload, "Compliance"),
    NavItem(CoreGuardRoute.Settings.route, "Settings", Icons.Filled.Settings, "Settings")
)

/**
 * Root composable for the entire app.
 *
 * Contains exactly one [NavHost] and one bottom navigation bar. All screens
 * are reachable through this single navigation graph.
 *
 * @param secretPortalVisible Shared toggle state controlled by the host Activity.
 *   When `true`, the [SecretPortalScreen] overlay is rendered above all content,
 *   mirroring the Shift+Alt+S secret-portal toggle from the web layer.
 */
@Composable
fun CoreGuardApp(
    secretPortalVisible: MutableState<Boolean> = remember { mutableStateOf(false) }
) {
    val navController = rememberNavController()

    Box {
        Scaffold(
            bottomBar = { CoreGuardBottomBar(navController) },
            containerColor = MaterialTheme.colorScheme.background
        ) { paddingValues ->
            NavHost(
                navController = navController,
                startDestination = CoreGuardRoute.Home.route,
                modifier = Modifier.padding(paddingValues)
            ) {
                composable(CoreGuardRoute.Home.route) {
                    HomeScreen(
                        onNavigateToScanner = {
                            navController.navigate(CoreGuardRoute.Scanner.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
                composable(CoreGuardRoute.Scanner.route) {
                    ScannerScreen()
                }
                composable(CoreGuardRoute.Timeline.route) {
                    TimelineScreen()
                }
                composable(CoreGuardRoute.Tools.route) {
                    ToolsScreen()
                }
                composable(CoreGuardRoute.Shield.route) {
                    ShieldScreen()
                }
                composable(CoreGuardRoute.SupplyChain.route) {
                    SupplyChainScreen()
                }
                composable(CoreGuardRoute.Compliance.route) {
                    ComplianceScreen()
                }
                composable(CoreGuardRoute.Settings.route) {
                    SettingsScreen()
                }
            }
        }

        if (secretPortalVisible.value) {
            SecretPortalScreen(onDismiss = { secretPortalVisible.value = false })
        }
    }
}

@Composable
private fun CoreGuardBottomBar(navController: NavController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        bottomNavItems.forEach { item ->
            val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.contentDescription
                    )
                },
                label = { Text(item.label) },
                selected = selected,
                onClick = {
                    navController.navigate(item.route) {
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

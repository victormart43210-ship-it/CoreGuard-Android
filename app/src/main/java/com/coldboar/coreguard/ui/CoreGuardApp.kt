package com.coldboar.coreguard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssuredWorkload
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.coldboar.coreguard.BillingProvider
import com.coldboar.coreguard.DemoBillingProvider
import com.coldboar.coreguard.ui.navigation.CoreGuardRoute
import com.coldboar.coreguard.ui.screens.ComplianceScreen
import com.coldboar.coreguard.ui.screens.HomeScreen
import com.coldboar.coreguard.ui.screens.PrivacyPolicyScreen
import com.coldboar.coreguard.ui.screens.ScannerScreen
import com.coldboar.coreguard.ui.screens.SecretPortalScreen
import com.coldboar.coreguard.ui.screens.SettingsScreen
import com.coldboar.coreguard.ui.screens.ShieldScreen
import com.coldboar.coreguard.ui.screens.TimelineScreen
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.MutedText

private data class NavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val bottomNavItems = listOf(
    NavItem(CoreGuardRoute.Home.route, "Home", Icons.Filled.Home),
    NavItem(CoreGuardRoute.Scanner.route, "Scanner", Icons.Filled.ManageSearch),
    NavItem(CoreGuardRoute.Shield.route, "Shield", Icons.Filled.Shield),
    NavItem(CoreGuardRoute.Compliance.route, "Compliance", Icons.Filled.AssuredWorkload),
    NavItem(CoreGuardRoute.Settings.route, "Settings", Icons.Filled.Settings)
)

private val routesWithoutBottomBar = setOf(
    CoreGuardRoute.PrivacyPolicy.route,
    CoreGuardRoute.Timeline.route
)

/**
 * Root composable for the entire app.
 *
 * Contains exactly one [NavHost] and one bottom navigation bar with five
 * primary destinations. All screens are reachable through this single graph.
 *
 * @param secretPortalVisible Shared toggle state controlled by the host Activity.
 * @param billingProvider Production [BillingProvider] from MainActivity. Demo default is for previews/tests only.
 */
@Composable
fun CoreGuardApp(
    secretPortalVisible: MutableState<Boolean> = remember { mutableStateOf(false) },
    billingProvider: BillingProvider = remember { DemoBillingProvider() }
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute !in routesWithoutBottomBar

    Box {
        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    CoreGuardBottomBar(navController)
                }
            },
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
                        },
                        onNavigateToTimeline = {
                            navController.navigate(CoreGuardRoute.Timeline.route)
                        }
                    )
                }
                composable(CoreGuardRoute.Scanner.route) {
                    ScannerScreen()
                }
                composable(CoreGuardRoute.Shield.route) {
                    ShieldScreen()
                }
                composable(CoreGuardRoute.Compliance.route) {
                    ComplianceScreen(
                        billingProvider = billingProvider,
                        onNavigateToSettings = {
                            navController.navigate(CoreGuardRoute.Settings.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
                composable(CoreGuardRoute.Timeline.route) {
                    TimelineScreen(
                        onBack = { navController.popBackStack() },
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
                composable(CoreGuardRoute.Settings.route) {
                    SettingsScreen(
                        billingProvider = billingProvider,
                        onNavigateToPrivacyPolicy = {
                            navController.navigate(CoreGuardRoute.PrivacyPolicy.route)
                        }
                    )
                }
                composable(CoreGuardRoute.PrivacyPolicy.route) {
                    PrivacyPolicyScreen(onBack = { navController.popBackStack() })
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

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(ElectricTeal.copy(alpha = 0.22f))
        )
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            bottomNavItems.forEach { item ->
                val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                NavigationBarItem(
                    icon = {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null
                        )
                    },
                    label = {
                        Text(item.label, style = MaterialTheme.typography.labelSmall)
                    },
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
                        indicatorColor = ElectricTeal.copy(alpha = 0.2f)
                    )
                )
            }
        }
    }
}

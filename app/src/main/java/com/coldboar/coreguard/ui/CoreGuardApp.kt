package com.coldboar.coreguard.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.filled.AssuredWorkload
import androidx.compose.material.icons.filled.Home
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.coldboar.coreguard.BillingProvider
import com.coldboar.coreguard.FirstRunStore
import com.coldboar.coreguard.ui.navigation.CoreGuardRoute
import com.coldboar.coreguard.ui.screens.ComplianceScreen
import com.coldboar.coreguard.ui.screens.ForensicJournalScreen
import com.coldboar.coreguard.ui.screens.HomeScreen
import com.coldboar.coreguard.ui.screens.OnboardingScreen
import com.coldboar.coreguard.ui.screens.OverlayProtectionMatrixScreen
import com.coldboar.coreguard.ui.screens.PrivacyPolicyScreen
import com.coldboar.coreguard.ui.screens.ScamGuardScreen
import com.coldboar.coreguard.ui.screens.ScannerScreen
import com.coldboar.coreguard.ui.screens.SecretPortalScreen
import com.coldboar.coreguard.ui.screens.SettingsScreen
import com.coldboar.coreguard.ui.screens.ShieldScreen
import com.coldboar.coreguard.ui.screens.SupplyChainScreen
import com.coldboar.coreguard.ui.screens.TimelineScreen
import com.coldboar.coreguard.ui.screens.ToolsScreen
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.MutedText
import com.coldboar.coreguard.ui.theme.RestrainedGold
import com.coldboar.coreguard.ui.theme.SurfacePewter

private data class NavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val bottomNavItems = listOf(
    NavItem(CoreGuardRoute.Home.route, "Home", Icons.Filled.Home),
    NavItem(CoreGuardRoute.Scanner.route, "Scanner", Icons.AutoMirrored.Filled.ManageSearch),
    NavItem(CoreGuardRoute.Shield.route, "Shield", Icons.Filled.Shield),
    NavItem(CoreGuardRoute.Compliance.route, "Compliance", Icons.Filled.AssuredWorkload),
    NavItem(CoreGuardRoute.Settings.route, "Settings", Icons.Filled.Settings)
)

private val routesWithoutBottomBar = setOf(
    CoreGuardRoute.Onboarding.route,
    CoreGuardRoute.PrivacyPolicy.route,
    CoreGuardRoute.Timeline.route,
    CoreGuardRoute.SupplyChain.route,
    CoreGuardRoute.Tools.route,
    CoreGuardRoute.OverlayMatrix.route,
    CoreGuardRoute.ForensicJournal.route,
    CoreGuardRoute.ScamGuard.route
)

private val tabRoutes = bottomNavItems.map { it.route }.toSet()

/**
 * Root composable for the entire app.
 *
 * Contains exactly one [NavHost] and one bottom navigation bar with five
 * primary destinations. All screens are reachable through this single graph.
 *
 * @param secretPortalVisible Shared toggle state controlled by the host Activity.
 * @param billingProvider Production [BillingProvider] from MainActivity (Play Billing).
 */
@Composable
fun CoreGuardApp(
    secretPortalVisible: MutableState<Boolean> = remember { mutableStateOf(false) },
    billingProvider: BillingProvider = rememberAppBillingProvider()
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute !in routesWithoutBottomBar

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun finishOnboarding(thenRoute: String = CoreGuardRoute.Home.route) {
        FirstRunStore.markOnboardingComplete(context)
        navController.navigate(thenRoute) {
            popUpTo(CoreGuardRoute.Onboarding.route) { inclusive = true }
            launchSingleTop = true
        }
    }

    LaunchedEffect(Unit) {
        if (!FirstRunStore.isOnboardingComplete(context)) {
            navController.navigate(CoreGuardRoute.Onboarding.route) {
                launchSingleTop = true
            }
        }
    }

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
                modifier = Modifier.padding(paddingValues),
                enterTransition = {
                    if (targetState.destination.route in tabRoutes &&
                        initialState.destination.route in tabRoutes
                    ) {
                        fadeIn(animationSpec = tween(220))
                    } else {
                        fadeIn(animationSpec = tween(240)) +
                            slideIntoContainer(
                                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                                animationSpec = tween(280),
                                initialOffset = { it / 20 }
                            )
                    }
                },
                exitTransition = {
                    fadeOut(animationSpec = tween(180))
                },
                popEnterTransition = {
                    fadeIn(animationSpec = tween(220)) +
                        slideIntoContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.End,
                            animationSpec = tween(260),
                            initialOffset = { it / 20 }
                        )
                },
                popExitTransition = {
                    fadeOut(animationSpec = tween(160))
                }
            ) {
                composable(CoreGuardRoute.Onboarding.route) {
                    OnboardingScreen(
                        onFinished = { finishOnboarding(CoreGuardRoute.Home.route) },
                        onRunFirstScan = { finishOnboarding(CoreGuardRoute.Scanner.route) }
                    )
                }
                composable(CoreGuardRoute.Home.route) {
                    HomeScreen(
                        onNavigateToScanner = {
                            navigateToTab(CoreGuardRoute.Scanner.route)
                        },
                        onNavigateToTimeline = {
                            navController.navigate(CoreGuardRoute.Timeline.route)
                        },
                        onNavigateToShield = {
                            navigateToTab(CoreGuardRoute.Shield.route)
                        },
                        onNavigateToTools = {
                            navController.navigate(CoreGuardRoute.Tools.route)
                        },
                        onNavigateToOverlayMatrix = {
                            navController.navigate(CoreGuardRoute.OverlayMatrix.route)
                        },
                        onNavigateToForensicJournal = {
                            navController.navigate(CoreGuardRoute.ForensicJournal.route)
                        },
                        onNavigateToScamGuard = {
                            navController.navigate(CoreGuardRoute.ScamGuard.route)
                        }
                    )
                }
                composable(CoreGuardRoute.Scanner.route) {
                    ScannerScreen(
                        billingProvider = billingProvider,
                        onUpgrade = {
                            navigateToTab(CoreGuardRoute.Settings.route)
                        }
                    )
                }
                composable(CoreGuardRoute.Shield.route) {
                    ShieldScreen()
                }
                composable(CoreGuardRoute.Compliance.route) {
                    ComplianceScreen(
                        billingProvider = billingProvider,
                        onUpgrade = {
                            navigateToTab(CoreGuardRoute.Settings.route)
                        },
                        onNavigateToSettings = {
                            navigateToTab(CoreGuardRoute.Settings.route)
                        },
                        onNavigateToSupplyChain = {
                            navController.navigate(CoreGuardRoute.SupplyChain.route)
                        }
                    )
                }
                composable(CoreGuardRoute.SupplyChain.route) {
                    SupplyChainScreen(onBack = { navController.popBackStack() })
                }
                composable(CoreGuardRoute.Tools.route) {
                    ToolsScreen(
                        onBack = { navController.popBackStack() },
                        onRunScan = { navigateToTab(CoreGuardRoute.Scanner.route) },
                        onOpenShield = { navigateToTab(CoreGuardRoute.Shield.route) },
                        onOpenTimeline = { navController.navigate(CoreGuardRoute.Timeline.route) },
                        onOpenOverlayMatrix = {
                            navController.navigate(CoreGuardRoute.OverlayMatrix.route)
                        },
                        onOpenForensicJournal = {
                            navController.navigate(CoreGuardRoute.ForensicJournal.route)
                        },
                        onOpenScamGuard = {
                            navController.navigate(CoreGuardRoute.ScamGuard.route)
                        },
                        isPremium = billingProvider.isPremium()
                    )
                }
                composable(CoreGuardRoute.OverlayMatrix.route) {
                    OverlayProtectionMatrixScreen(onBack = { navController.popBackStack() })
                }
                composable(CoreGuardRoute.ForensicJournal.route) {
                    ForensicJournalScreen(onBack = { navController.popBackStack() })
                }
                composable(CoreGuardRoute.ScamGuard.route) {
                    ScamGuardScreen(onBack = { navController.popBackStack() })
                }
                composable(CoreGuardRoute.Timeline.route) {
                    TimelineScreen(
                        billingProvider = billingProvider,
                        onUpgrade = {
                            navigateToTab(CoreGuardRoute.Settings.route)
                        },
                        onBack = { navController.popBackStack() },
                        onNavigateToScanner = {
                            navigateToTab(CoreGuardRoute.Scanner.route)
                        }
                    )
                }
                composable(CoreGuardRoute.Settings.route) {
                    SettingsScreen(
                        billingProvider = billingProvider,
                        onNavigateToPrivacyPolicy = {
                            navController.navigate(CoreGuardRoute.PrivacyPolicy.route)
                        },
                        onNavigateToTools = {
                            navController.navigate(CoreGuardRoute.Tools.route)
                        },
                        onRunScan = {
                            navigateToTab(CoreGuardRoute.Scanner.route)
                        },
                        onOpenShield = {
                            navigateToTab(CoreGuardRoute.Shield.route)
                        },
                        onOpenTimeline = {
                            navController.navigate(CoreGuardRoute.Timeline.route)
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
                .background(
                    brush = Brush.horizontalGradient(
                        listOf(
                            ElectricTeal.copy(alpha = 0.05f),
                            ElectricTeal.copy(alpha = 0.45f),
                            RestrainedGold.copy(alpha = 0.35f),
                            ElectricTeal.copy(alpha = 0.05f)
                        )
                    )
                )
        )
        NavigationBar(
            containerColor = SurfacePewter.copy(alpha = 0.97f),
            tonalElevation = 0.dp
        ) {
            bottomNavItems.forEach { item ->
                val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                NavigationBarItem(
                    icon = {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label
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

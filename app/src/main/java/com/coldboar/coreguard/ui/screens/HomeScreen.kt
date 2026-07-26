package com.coldboar.coreguard.ui.screens

import androidx.compose.runtime.Composable
import com.coldboar.coreguard.ui.dashboard.EliteDashboardScreen

/**
 * Home tab entry — CG Elite sacred-geometry dashboard.
 *
 * Legacy pewter Home UI lived here; Elite dashboard is now the default Home
 * surface with real Guardian Score / CPU / RAM / Nemesis / Shield / swarm metrics.
 */
@Composable
fun HomeScreen(
    onNavigateToScanner: () -> Unit,
    onNavigateToTimeline: () -> Unit = {},
    onNavigateToShield: () -> Unit = {},
    onNavigateToTools: () -> Unit = {}
) {
    EliteDashboardScreen(
        onNavigateToScanner = onNavigateToScanner,
        onNavigateToTimeline = onNavigateToTimeline,
        onNavigateToShield = onNavigateToShield,
        onNavigateToTools = onNavigateToTools
    )
}

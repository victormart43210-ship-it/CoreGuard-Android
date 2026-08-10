package com.coldboar.coreguard.ui.screens

import androidx.compose.runtime.Composable
import com.coldboar.coreguard.ui.dashboard.EliteDashboardScreen

/**
 * Home tab entry — Elite dashboard with Guardian Score, device metrics,
 * Nemesis / Shield / swarm status, and deep-link entry points.
 */
@Composable
fun HomeScreen(
    onNavigateToScanner: () -> Unit,
<<<<<<< HEAD
    onNavigateToTimeline: () -> Unit = {},
    onNavigateToShield: () -> Unit = {},
    onNavigateToTools: () -> Unit = {},
    onNavigateToOverlayMatrix: () -> Unit = {},
    onNavigateToForensicJournal: () -> Unit = {},
    onNavigateToScamGuard: () -> Unit = {},
    onNavigateToGuardian: () -> Unit = {}
=======
    onNavigateToTimeline: () -> Unit,
    onNavigateToShield: () -> Unit,
    onNavigateToTools: () -> Unit,
    onNavigateToOverlayMatrix: () -> Unit,
    onNavigateToForensicJournal: () -> Unit,
    onNavigateToScamGuard: () -> Unit
>>>>>>> origin/main
) {
    EliteDashboardScreen(
        onNavigateToScanner = onNavigateToScanner,
        onNavigateToTimeline = onNavigateToTimeline,
        onNavigateToShield = onNavigateToShield,
        onNavigateToTools = onNavigateToTools,
        onNavigateToOverlayMatrix = onNavigateToOverlayMatrix,
        onNavigateToForensicJournal = onNavigateToForensicJournal,
        onNavigateToScamGuard = onNavigateToScamGuard,
        onNavigateToGuardian = onNavigateToGuardian
    )
}

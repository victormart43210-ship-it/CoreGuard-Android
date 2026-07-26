package com.coldboar.coreguard.ui.screens

import androidx.compose.runtime.Composable
import com.coldboar.coreguard.ui.dashboard.EliteDashboardScreen

/**
 * Home tab entry — CG Elite sacred-geometry dashboard.
 *
 * Legacy pewter Home UI lived here; Elite dashboard is now the default Home
 * surface with real Guardian Score / CPU / RAM / Nemesis / Shield / swarm metrics
 * plus M17 Dynamic Threat Score / Scam Guard / forensic entry points.
 */
@Composable
fun HomeScreen(
    onNavigateToScanner: () -> Unit,
    onNavigateToTimeline: () -> Unit = {},
    onNavigateToShield: () -> Unit = {},
    onNavigateToTools: () -> Unit = {},
    onNavigateToOverlayMatrix: () -> Unit = {},
    onNavigateToForensicJournal: () -> Unit = {},
    onNavigateToScamGuard: () -> Unit = {},
    onNavigateToGuardian: () -> Unit = {}
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

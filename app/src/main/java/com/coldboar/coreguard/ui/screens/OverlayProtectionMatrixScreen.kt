package com.coldboar.coreguard.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.coldboar.coreguard.SecurityCheckResult
import com.coldboar.coreguard.SecurityCheckState
import com.coldboar.coreguard.defense.AccessibilityAbuseEvaluator
import com.coldboar.coreguard.defense.OverlayAbuseEvaluator
import com.coldboar.coreguard.defense.SideloadRiskEvaluator
import com.coldboar.coreguard.elite.EliteModule
import com.coldboar.coreguard.elite.ForensicJournal
import com.coldboar.coreguard.ui.components.CoreGuardCard
import com.coldboar.coreguard.ui.components.ScreenAtmosphere
import com.coldboar.coreguard.ui.components.SubScreenTopBar
import com.coldboar.coreguard.ui.theme.AttentionAmber
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.HighRed
import com.coldboar.coreguard.ui.theme.MutedText
import com.coldboar.coreguard.ui.theme.SafeGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Accessibility & Overlay Protection Matrix — evidence-backed surface audit.
 * Does not silently kill other apps' overlays (Play policy / privilege limits);
 * it exposes the matrix and journals elevated findings.
 */
@Composable
fun OverlayProtectionMatrixScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var rows by remember { mutableStateOf<List<SecurityCheckResult>>(emptyList()) }

    LaunchedEffect(Unit) {
        rows = withContext(Dispatchers.IO) {
            val list = listOf(
                OverlayAbuseEvaluator(context).evaluate(),
                AccessibilityAbuseEvaluator(context).evaluate(),
                SideloadRiskEvaluator(context).evaluate()
            )
            list.filter { it.state != SecurityCheckState.PASS }.forEach { r ->
                runCatching {
                    EliteModule.appendJournal(
                        context,
                        when (r.id) {
                            "overlay_abuse" -> ForensicJournal.EventKind.OVERLAY_ALERT
                            "accessibility_abuse" -> ForensicJournal.EventKind.ACCESSIBILITY_ALERT
                            else -> ForensicJournal.EventKind.TAMPER
                        },
                        packageName = context.packageName,
                        details = "${r.displayName}: ${r.explanation}",
                        metadata = mapOf("state" to r.state.name, "checkId" to r.id)
                    )
                }
            }
            list
        }
    }

    ScreenAtmosphere(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SubScreenTopBar(
            title = "Overlay Protection Matrix",
            subtitle = "Anti-overlay · Accessibility · Sideload surfaces",
            onBack = onBack
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "CoreGuard inspects draw-over-apps and third-party Accessibility holders. " +
                "Elevated findings are chained into the Forensic Journal. " +
                "Silent remote overlay-killing requires privileges Android does not grant to Play apps.",
            style = MaterialTheme.typography.bodySmall,
            color = MutedText,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        rows.forEach { row ->
            MatrixRowCard(row)
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun MatrixRowCard(result: SecurityCheckResult) {
    val color = when (result.state) {
        SecurityCheckState.PASS -> SafeGreen
        SecurityCheckState.WARN -> AttentionAmber
        SecurityCheckState.FAIL -> HighRed
    }
    CoreGuardCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = result.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = ElectricTeal,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = result.state.name,
                style = MaterialTheme.typography.labelLarge,
                color = color
            )
            Text(
                text = result.explanation,
                style = MaterialTheme.typography.bodyMedium,
                color = MutedText
            )
        }
    }
}

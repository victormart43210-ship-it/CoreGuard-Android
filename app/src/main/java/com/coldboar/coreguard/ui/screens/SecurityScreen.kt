package com.coldboar.coreguard.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.coldboar.coreguard.BuildTypeCheckEvaluator
import com.coldboar.coreguard.DebuggerCheckEvaluator
import com.coldboar.coreguard.EmulatorCheckEvaluator
import com.coldboar.coreguard.RootCheckEvaluator
import com.coldboar.coreguard.SecurityCheckResult
import com.coldboar.coreguard.SecurityCheckState
import com.coldboar.coreguard.SecurityUtils
import com.coldboar.coreguard.SignatureCheckEvaluator
import com.coldboar.coreguard.SpywareScanEvaluator
import com.coldboar.coreguard.ui.components.CardSpacer
import com.coldboar.coreguard.ui.components.SecurityCheckRow
import com.coldboar.coreguard.ui.components.SectionHeader
import com.coldboar.coreguard.ui.components.StatusCard
import com.coldboar.coreguard.ui.components.toColor
import com.coldboar.coreguard.ui.theme.ElectricTeal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Dedicated security screen.
 *
 * Runs all six security-check evaluators and displays the results.
 * Also provides navigation shortcuts to the Nemesis scanner and Privacy Shield.
 */
@Composable
fun SecurityScreen(
    onNavigateToScanner: () -> Unit,
    onNavigateToShield: () -> Unit
) {
    val context = LocalContext.current
    var results by remember { mutableStateOf<List<SecurityCheckResult>>(emptyList()) }

    LaunchedEffect(Unit) {
        val certSha256 = withContext(Dispatchers.IO) { SecurityUtils.getAppCertSha256(context) }
        val evaluators = listOf(
            SpywareScanEvaluator(),
            DebuggerCheckEvaluator(),
            EmulatorCheckEvaluator(),
            RootCheckEvaluator(),
            BuildTypeCheckEvaluator(),
            SignatureCheckEvaluator(actualSha256 = { certSha256 })
        )
        results = evaluators.map { it.evaluate() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        SectionHeader(
            title = "Security",
            subtitle = "Heuristic checks for common device integrity issues"
        )

        Spacer(Modifier.height(20.dp))

        if (results.isNotEmpty()) {
            val overallState = when {
                results.any { it.state == SecurityCheckState.FAIL } -> SecurityCheckState.FAIL
                results.any { it.state == SecurityCheckState.WARN } -> SecurityCheckState.WARN
                else -> SecurityCheckState.PASS
            }
            val overallLabel = when (overallState) {
                SecurityCheckState.PASS -> "OVERALL: PASS"
                SecurityCheckState.WARN -> "OVERALL: WARN"
                SecurityCheckState.FAIL -> "OVERALL: FAIL"
            }

            StatusCard {
                Text(
                    text = overallLabel,
                    style = MaterialTheme.typography.titleLarge,
                    color = overallState.toColor()
                )
                Spacer(Modifier.height(12.dp))
                results.forEach { result -> SecurityCheckRow(result) }
            }

            CardSpacer()
        }

        StatusCard {
            Text("Deep Scan", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Run the Nemesis privacy scanner to check installed packages, " +
                    "processes, and files against known threat indicators.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onNavigateToScanner,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricTeal)
            ) {
                Text("Open Nemesis Scanner", color = Color.Black)
            }
        }

        CardSpacer()

        StatusCard {
            Text("Privacy Shield", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Enable the on-device VPN to block connections to known tracking servers.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onNavigateToShield,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricTeal)
            ) {
                Text("Open Privacy Shield", color = Color.Black)
            }
        }
    }
}

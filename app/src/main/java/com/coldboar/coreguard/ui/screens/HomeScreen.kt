package com.coldboar.coreguard.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.coldboar.coreguard.BuildTypeCheckEvaluator
import com.coldboar.coreguard.CpuUsageCalculator
import com.coldboar.coreguard.DebuggerCheckEvaluator
import com.coldboar.coreguard.EmulatorCheckEvaluator
import com.coldboar.coreguard.MemoryUsageCalculator
import com.coldboar.coreguard.RootCheckEvaluator
import com.coldboar.coreguard.SecurityCheckResult
import com.coldboar.coreguard.SecurityCheckState
import com.coldboar.coreguard.SecurityUtils
import com.coldboar.coreguard.SignatureCheckEvaluator
import com.coldboar.coreguard.SpywareScanEvaluator
import com.coldboar.coreguard.ui.theme.AttentionAmber
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.HighRed
import com.coldboar.coreguard.ui.theme.SafeGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(onNavigateToScanner: () -> Unit) {
    val context = LocalContext.current

    var ramText by remember { mutableStateOf("–") }
    var cpuText by remember { mutableStateOf("CPU: Measuring…") }
    var securityResults by remember { mutableStateOf<List<SecurityCheckResult>>(emptyList()) }

    LaunchedEffect(Unit) {
        // One-time: evaluate security checks (fast, no network I/O)
        val certSha256 = withContext(Dispatchers.IO) { SecurityUtils.getAppCertSha256(context) }
        val evaluators = listOf(
            SpywareScanEvaluator(),
            DebuggerCheckEvaluator(),
            EmulatorCheckEvaluator(),
            RootCheckEvaluator(),
            BuildTypeCheckEvaluator(),
            SignatureCheckEvaluator(actualSha256 = { certSha256 })
        )
        securityResults = evaluators.map { it.evaluate() }

        // Continuous: poll RAM / CPU every 2 s
        CpuUsageCalculator.reset()
        while (true) {
            val usedRam = MemoryUsageCalculator.getUsedRamBytes(context)
            val totalRam = MemoryUsageCalculator.getTotalRamBytes(context)
            ramText = if (usedRam != null && totalRam != null) {
                "${MemoryUsageCalculator.formatBytes(usedRam)} / ${MemoryUsageCalculator.formatBytes(totalRam)}"
            } else "–"

            val cpu = CpuUsageCalculator.getUsagePercent()
            cpuText = if (cpu != null) "CPU: $cpu%" else "CPU: Measuring…"

            delay(2_000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "CoreGuard",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = "Security & device monitoring",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(20.dp))

        // System health card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("System Health", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("RAM: $ramText", style = MaterialTheme.typography.bodyMedium)
                Text(cpuText, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Security status card
        if (securityResults.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Security Status", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))

                    val overallState = when {
                        securityResults.any { it.state == SecurityCheckState.FAIL } -> SecurityCheckState.FAIL
                        securityResults.any { it.state == SecurityCheckState.WARN } -> SecurityCheckState.WARN
                        else -> SecurityCheckState.PASS
                    }
                    val overallLabel = when (overallState) {
                        SecurityCheckState.PASS -> "OVERALL: PASS"
                        SecurityCheckState.WARN -> "OVERALL: WARN"
                        SecurityCheckState.FAIL -> "OVERALL: FAIL"
                    }
                    Text(
                        text = overallLabel,
                        style = MaterialTheme.typography.titleLarge,
                        color = overallState.toColor()
                    )

                    Spacer(Modifier.height(8.dp))

                    securityResults.forEach { result ->
                        SecurityCheckRow(result)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onNavigateToScanner,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = ElectricTeal)
        ) {
            Text("Open Nemesis Scanner", color = Color.Black)
        }
    }
}

@Composable
private fun SecurityCheckRow(result: SecurityCheckResult) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val icon = when (result.state) {
            SecurityCheckState.PASS -> "✅"
            SecurityCheckState.WARN -> "⚠️"
            SecurityCheckState.FAIL -> "❌"
        }
        Text(
            text = "$icon ${result.displayName}",
            style = MaterialTheme.typography.bodyMedium,
            color = result.state.toColor(),
            modifier = Modifier.weight(1f)
        )
    }
    Text(
        text = result.explanation,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(start = 28.dp, bottom = 4.dp)
    )
}

private fun SecurityCheckState.toColor(): Color = when (this) {
    SecurityCheckState.PASS -> SafeGreen
    SecurityCheckState.WARN -> AttentionAmber
    SecurityCheckState.FAIL -> HighRed
}

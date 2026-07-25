package com.coldboar.coreguard.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.unit.dp
import com.coldboar.coreguard.CpuUsageCalculator
import com.coldboar.coreguard.MemoryUsageCalculator
import com.coldboar.coreguard.ui.components.CardSpacer
import com.coldboar.coreguard.ui.components.SectionHeader
import com.coldboar.coreguard.ui.components.StatRow
import com.coldboar.coreguard.ui.components.StatusCard
import com.coldboar.coreguard.ui.theme.AttentionAmber
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.HighRed
import com.coldboar.coreguard.ui.theme.SafeGreen
import kotlinx.coroutines.delay

/**
 * Performance monitoring screen.
 *
 * Shows live RAM usage and simulated CPU readings, updated every 2 seconds.
 */
@Composable
fun PerformanceScreen() {
    val context = LocalContext.current

    var usedRam by remember { mutableStateOf<Long?>(null) }
    var totalRam by remember { mutableStateOf<Long?>(null) }
    var cpuPercent by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        CpuUsageCalculator.reset()
        while (true) {
            usedRam = MemoryUsageCalculator.getUsedRamBytes(context)
            totalRam = MemoryUsageCalculator.getTotalRamBytes(context)
            cpuPercent = CpuUsageCalculator.getUsagePercent()
            delay(2_000)
        }
    }

    val usedRamText = if (usedRam != null) MemoryUsageCalculator.formatBytes(usedRam!!) else "–"
    val totalRamText = if (totalRam != null) MemoryUsageCalculator.formatBytes(totalRam!!) else "–"
    val ramFraction: Float = if (usedRam != null && totalRam != null && totalRam!! > 0L) {
        (usedRam!!.toFloat() / totalRam!!.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val ramColor = when {
        ramFraction > 0.85f -> HighRed
        ramFraction > 0.65f -> AttentionAmber
        else -> SafeGreen
    }

    val cpuText = if (cpuPercent != null) "$cpuPercent%" else "Measuring…"
    val cpuFraction: Float = (cpuPercent ?: 0).toFloat() / 100f
    val cpuColor = when {
        cpuFraction > 0.85f -> HighRed
        cpuFraction > 0.65f -> AttentionAmber
        else -> ElectricTeal
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        SectionHeader(
            title = "Performance",
            subtitle = "Live device resource usage — updated every 2 s"
        )

        Spacer(Modifier.height(20.dp))

        StatusCard {
            Text("Memory (RAM)", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            StatRow(label = "Used", value = usedRamText, valueColor = ramColor)
            StatRow(label = "Total", value = totalRamText)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { ramFraction },
                color = ramColor,
                trackColor = MaterialTheme.colorScheme.surface
            )
        }

        CardSpacer()

        StatusCard {
            Text("CPU Usage (simulated)", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "⚠️ CPU readings are simulated — a real /proc/stat reader requires root on modern Android.",
                style = MaterialTheme.typography.bodySmall,
                color = AttentionAmber
            )
            Spacer(Modifier.height(8.dp))
            StatRow(label = "Usage", value = cpuText, valueColor = cpuColor)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { cpuFraction },
                color = cpuColor,
                trackColor = MaterialTheme.colorScheme.surface
            )
        }
    }
}

package com.coldboar.coreguard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coldboar.coreguard.BillingProvider
import com.coldboar.coreguard.DemoBillingProvider
import com.coldboar.coreguard.EntitlementPolicy
import com.coldboar.coreguard.mvt.ScanHistoryStore
import com.coldboar.coreguard.mvt.ScanVerdict
import com.coldboar.coreguard.ui.components.PremiumUpsellCard
import com.coldboar.coreguard.ui.theme.AttentionAmber
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.HighRed
import com.coldboar.coreguard.ui.theme.MutedText
import com.coldboar.coreguard.ui.theme.SafeGreen
import com.coldboar.coreguard.ui.theme.SurfacePewter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TimelineScreen(
    billingProvider: BillingProvider = remember { DemoBillingProvider() },
    onUpgrade: () -> Unit = {}
) {
    val context = LocalContext.current
    val policy = remember(billingProvider.isPremium()) { EntitlementPolicy(billingProvider) }
    var records by remember { mutableStateOf<List<ScanHistoryStore.ScanRecord>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        records = withContext(Dispatchers.IO) { ScanHistoryStore.load(context) }
        loading = false
    }

    val visible = records.take(policy.maxTimelineEntries())
    val hiddenCount = (records.size - visible.size).coerceAtLeast(0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Scan Timeline",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = if (policy.isPremium()) {
                "Your full integrity ledger — up to ${EntitlementPolicy.PREMIUM_TIMELINE_ENTRIES} scans."
            } else {
                "Free shows your last ${EntitlementPolicy.FREE_TIMELINE_ENTRIES} scans. Premium keeps the longer ledger."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MutedText
        )

        Spacer(Modifier.height(24.dp))

        when {
            loading -> {
                Text("Loading history…", style = MaterialTheme.typography.bodyMedium, color = MutedText)
            }
            records.isEmpty() -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfacePewter),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("No scans yet", style = MaterialTheme.typography.titleMedium, color = ElectricTeal)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Run a Nemesis scan to begin recording your device integrity timeline.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedText,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            else -> {
                visible.forEachIndexed { index, record ->
                    ScanTimelineEntry(record = record, isLast = index == visible.lastIndex && hiddenCount == 0)
                    if (index < visible.lastIndex) Spacer(Modifier.height(2.dp))
                }
                if (hiddenCount > 0 && !policy.isPremium()) {
                    Spacer(Modifier.height(16.dp))
                    PremiumUpsellCard(
                        title = "See the full timeline",
                        body = "$hiddenCount older scan(s) are hidden on Free. Premium unlocks up to " +
                            "${EntitlementPolicy.PREMIUM_TIMELINE_ENTRIES} entries so you can spot patterns over time.",
                        onUpgrade = onUpgrade
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ScanTimelineEntry(record: ScanHistoryStore.ScanRecord, isLast: Boolean) {
    val verdictColor = when (record.verdict) {
        ScanVerdict.CLEAN -> SafeGreen
        ScanVerdict.SUSPICIOUS -> AttentionAmber
        ScanVerdict.INFECTED -> HighRed
    }
    val verdictLabel = when (record.verdict) {
        ScanVerdict.CLEAN -> "CLEAN"
        ScanVerdict.SUSPICIOUS -> "SUSPICIOUS"
        ScanVerdict.INFECTED -> "THREAT DETECTED"
    }
    val dateFormat = SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault())
    val dateStr = dateFormat.format(Date(record.timestampMs))

    Row(modifier = Modifier.fillMaxWidth()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(end = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(verdictColor)
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(60.dp)
                        .background(verdictColor.copy(alpha = 0.3f))
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            colors = CardDefaults.cardColors(containerColor = SurfacePewter),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = verdictLabel,
                        style = MaterialTheme.typography.titleSmall,
                        color = verdictColor,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${record.durationMillis}ms",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedText
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "${record.scannedArtifacts} checked",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedText
                    )
                    Text(
                        text = "${record.indicatorCount} signatures",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedText
                    )
                    if (record.detectionCount > 0) {
                        Text(
                            text = "${record.detectionCount} flagged",
                            style = MaterialTheme.typography.bodySmall,
                            color = HighRed,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

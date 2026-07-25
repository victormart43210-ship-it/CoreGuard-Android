package com.coldboar.coreguard.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coldboar.coreguard.data.local.entity.QuillaHypothesisEntity
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuillaDashboardScreen(
    viewModel: DashboardViewModel
) {
    val hypotheses by viewModel.activeHypotheses.collectAsState(initial = emptyList())
    val isSyncing by viewModel.isSyncing.collectAsState()
    val activeIocCount by viewModel.activeIocCount.collectAsState()

    var selectedHypothesis by remember { mutableStateOf<QuillaHypothesisEntity?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CoreGuard Elite", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF121212),
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF0A0A0C)
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Overall System Status
            item {
                val hasHighRisk = hypotheses.any { it.confidence >= 0.75f }
                SystemStatusCard(hasHighRisk = hasHighRisk)
            }

            // 2. Quilla Intelligence Engine Header
            item {
                QuillaEngineCard(
                    activeIocCount = activeIocCount,
                    isSyncing = isSyncing,
                    onSyncRequested = { viewModel.triggerAmnestySync() }
                )
            }

            // 3. Active Threat Hypotheses Section
            item {
                Text(
                    text = "ACTIVE HYPOTHESES (${hypotheses.size}) — TAP TO ACT",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold
                )
            }

            if (hypotheses.isEmpty()) {
                item { EmptyHypothesisCard() }
            } else {
                items(hypotheses, key = { it.id }) { hypothesis ->
                    HypothesisItemCard(
                        hypothesis = hypothesis,
                        onClick = { selectedHypothesis = hypothesis }
                    )
                }
            }

            // 4. Shield Status Indicators
            item {
                ShieldStatusCard(
                    title = "📱 RASP & System Integrity",
                    statusText = "Root Clean • Integrity Verified",
                    isWarning = false
                )
            }

            item {
                ShieldStatusCard(
                    title = "🌐 Network Shield",
                    statusText = "Connected to Untrusted Wi-Fi",
                    isWarning = true
                )
            }
        }

        // Incident Mitigation Bottom Sheet
        val currentHypothesis = selectedHypothesis
        if (currentHypothesis != null) {
            ModalBottomSheet(
                onDismissRequest = { selectedHypothesis = null },
                sheetState = sheetState,
                containerColor = Color(0xFF16161A),
                scrimColor = Color.Black.copy(alpha = 0.7f)
            ) {
                IncidentResponseBottomSheetContent(
                    hypothesis = currentHypothesis,
                    onQuarantine = {
                        viewModel.quarantinePackage(currentHypothesis.id)
                        selectedHypothesis = null
                    },
                    onKillProcess = {
                        viewModel.terminateTargetProcess(currentHypothesis.id)
                        selectedHypothesis = null
                    },
                    onDismissAlert = {
                        viewModel.dismissHypothesis(currentHypothesis.id)
                        selectedHypothesis = null
                    }
                )
            }
        }
    }
}

@Composable
fun HypothesisItemCard(
    hypothesis: QuillaHypothesisEntity,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E24)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .border(1.dp, Color(0xFFE53935).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = hypothesis.hypothesisType,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color(0xFFFFB74D)
                )
                Text(
                    text = "${(hypothesis.confidence * 100).toInt()}% Confidence",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF6B6B)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = hypothesis.summary,
                fontSize = 13.sp,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tap to view evidence & mitigate ➔",
                fontSize = 11.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun IncidentResponseBottomSheetContent(
    hypothesis: QuillaHypothesisEntity,
    onQuarantine: () -> Unit,
    onKillProcess: () -> Unit,
    onDismissAlert: () -> Unit
) {
    val targetPackage = remember(hypothesis.evidenceJson) {
        try {
            JSONObject(hypothesis.evidenceJson).optString("packageName", "Unknown Target")
        } catch (e: Exception) {
            "Unknown Target"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .padding(bottom = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🚨 INCIDENT ACTION SHEET",
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFF6B6B),
                fontSize = 14.sp
            )
            Surface(
                color = Color(0xFF3B1010),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "${(hypothesis.confidence * 100).toInt()}% RISK",
                    color = Color(0xFFFF6B6B),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Target Package:",
            fontSize = 11.sp,
            color = Color.Gray
        )
        Text(
            text = targetPackage,
            fontSize = 15.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0A0A0C), RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Text(
                text = hypothesis.summary,
                fontSize = 12.sp,
                color = Color.LightGray
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onQuarantine,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("🛡️ Quarantine Package (Disable & Isolate)", color = Color.White, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onKillProcess,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D3250)),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("⚡ Kill Active Process", color = Color.White)
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onDismissAlert,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Dismiss as False Positive", color = Color.Gray)
        }
    }
}

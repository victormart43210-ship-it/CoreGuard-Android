package com.coldboar.coreguard.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.coldboar.coreguard.mvt.ScanHistoryStore
import com.coldboar.coreguard.mvt.ScanVerdict
import com.coldboar.coreguard.ui.theme.AttentionAmber
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.HighRed
import com.coldboar.coreguard.ui.theme.MutedText
import com.coldboar.coreguard.ui.theme.RestrainedGold
import com.coldboar.coreguard.ui.theme.SafeGreen
import com.coldboar.coreguard.ui.theme.premiumTween
import com.coldboar.coreguard.ui.theme.rememberMotionEnabled

/**
 * Pure helpers for threat-timeline visualization (JVM-testable).
 *
 * [records] should be newest-first (as [ScanHistoryStore.load] returns).
 * Series are oldest→newest for left-to-right charts.
 */
object ThreatTimelineViz {

    fun chronological(records: List<ScanHistoryStore.ScanRecord>): List<ScanHistoryStore.ScanRecord> =
        records.asReversed()

    /** Detection counts normalized 0f..1f for sparkline height. */
    fun detectionHeights(recordsChronological: List<ScanHistoryStore.ScanRecord>): List<Float> {
        if (recordsChronological.isEmpty()) return emptyList()
        val max = recordsChronological.maxOf { it.detectionCount }.coerceAtLeast(1)
        return recordsChronological.map { it.detectionCount.toFloat() / max.toFloat() }
    }

    fun verdictSummary(records: List<ScanHistoryStore.ScanRecord>): String {
        if (records.isEmpty()) return "No privacy checks yet"
        val clean = records.count { it.verdict == ScanVerdict.CLEAN }
        val suspicious = records.count { it.verdict == ScanVerdict.SUSPICIOUS }
        val infected = records.count { it.verdict == ScanVerdict.INFECTED }
        val flagged = records.sumOf { it.detectionCount }
        return buildString {
            append("${records.size} checks: ")
            append("$clean looked clean, ")
            append("$suspicious possible risk, ")
            append("$infected with indicators matched. ")
            append("$flagged total flags across history.")
        }
    }
}

@Composable
fun ThreatTimelineChart(
    records: List<ScanHistoryStore.ScanRecord>,
    modifier: Modifier = Modifier
) {
    val chronological = remember(records) { ThreatTimelineViz.chronological(records) }
    val heights = remember(chronological) { ThreatTimelineViz.detectionHeights(chronological) }
    val summary = remember(records) { ThreatTimelineViz.verdictSummary(records) }
    val motionEnabled = rememberMotionEnabled()
    val reveal by animateFloatAsState(
        targetValue = 1f,
        animationSpec = if (motionEnabled) premiumTween(700) else androidx.compose.animation.core.tween(0),
        label = "timelineReveal"
    )

    if (chronological.isEmpty()) return

    CoreGuardCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                heading()
                contentDescription = "Threat timeline chart. $summary"
            }
    ) {
        Column(modifier = Modifier.padding(4.dp)) {
            Text(
                text = "THREAT TIMELINE",
                style = MaterialTheme.typography.labelLarge,
                color = RestrainedGold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Privacy-check history on this device — indicator matches over time, not a guarantee.",
                style = MaterialTheme.typography.bodySmall,
                color = MutedText
            )
            Spacer(modifier = Modifier.height(12.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                val n = chronological.size
                val barSlot = size.width / n.coerceAtLeast(1)
                val chartBottom = size.height * 0.88f
                val chartTop = size.height * 0.12f
                val chartH = chartBottom - chartTop

                // Soft baseline
                drawLine(
                    color = ElectricTeal.copy(alpha = 0.25f),
                    start = Offset(0f, chartBottom),
                    end = Offset(size.width, chartBottom),
                    strokeWidth = 1.5f
                )

                val path = Path()
                chronological.forEachIndexed { index, record ->
                    val h = heights.getOrElse(index) { 0f } * reveal
                    val barH = (0.12f + 0.88f * h) * chartH
                    val left = index * barSlot + barSlot * 0.18f
                    val width = barSlot * 0.64f
                    val top = chartBottom - barH
                    val color = when (record.verdict) {
                        ScanVerdict.CLEAN -> SafeGreen
                        ScanVerdict.SUSPICIOUS -> AttentionAmber
                        ScanVerdict.INFECTED -> HighRed
                    }
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            listOf(color.copy(alpha = 0.95f), color.copy(alpha = 0.35f))
                        ),
                        topLeft = Offset(left, top),
                        size = Size(width, barH),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                    val cx = left + width / 2f
                    val cy = top
                    if (index == 0) path.moveTo(cx, cy) else path.lineTo(cx, cy)
                }
                if (n >= 2) {
                    drawPath(
                        path = path,
                        color = ElectricTeal.copy(alpha = 0.75f * reveal),
                        style = Stroke(width = 3f, cap = StrokeCap.Round)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LegendDot(SafeGreen, "Looked clean")
                LegendDot(AttentionAmber, "Possible risk")
                LegendDot(HighRed, "Indicators matched")
            }
        }
    }
}

@Composable
private fun LegendDot(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MutedText)
    }
}

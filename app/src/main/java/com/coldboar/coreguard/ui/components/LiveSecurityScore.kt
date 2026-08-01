package com.coldboar.coreguard.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coldboar.coreguard.GuardianRank
import com.coldboar.coreguard.GuardianScore
import com.coldboar.coreguard.ui.theme.AttentionAmber
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.HighRed
import com.coldboar.coreguard.ui.theme.MutedText
import com.coldboar.coreguard.ui.theme.RestrainedGold
import com.coldboar.coreguard.ui.theme.SafeGreen
import com.coldboar.coreguard.ui.theme.rememberMotionEnabled
import com.coldboar.coreguard.ui.theme.premiumTween

/**
 * Live Guardian Score instrument: animates when the on-device check summary
 * changes. Honesty: this is a weighted local-check summary, not spyware absence
 * proof or cloud threat intel.
 */
@Composable
fun LiveSecurityScore(
    score: Int?,
    rank: GuardianRank?,
    liveUpdating: Boolean,
    modifier: Modifier = Modifier
) {
    val motionEnabled = rememberMotionEnabled()
    val target = (score ?: 0).coerceIn(0, 100) / 100f
    val animatedProgress by animateFloatAsState(
        targetValue = target,
        animationSpec = if (motionEnabled) {
            premiumTween(durationMillis = 900)
        } else {
            tween(durationMillis = 0)
        },
        label = "liveScoreProgress"
    )
    val accent = scoreAccent(score)
    val displayScore = score?.toString() ?: "—"
    val rankLabel = rank?.userLabel ?: "Checking local wards"
    val liveHint = if (liveUpdating) "Updating while this screen is open." else "Paused."

    CoreGuardCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                heading()
                liveRegion = LiveRegionMode.Polite
                contentDescription = buildString {
                    append("Security score $displayScore. ")
                    append("$rankLabel. ")
                    append("On-device check summary. ")
                    append(liveHint)
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "SECURITY SCORE",
                style = MaterialTheme.typography.labelLarge,
                color = RestrainedGold,
                letterSpacing = 2.sp
            )
            Box(contentAlignment = Alignment.Center) {
                PrecisionScoreRing(
                    progress = animatedProgress,
                    accent = accent,
                    size = 196.dp,
                    active = liveUpdating && motionEnabled
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = displayScore,
                        style = MaterialTheme.typography.displaySmall,
                        color = accent,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = rankLabel,
                        style = MaterialTheme.typography.titleSmall,
                        color = ElectricTeal,
                        textAlign = TextAlign.Center
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = rank?.userGuidance
                    ?: "Running on-device security checks…",
                style = MaterialTheme.typography.bodySmall,
                color = MutedText,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            Text(
                text = if (liveUpdating) {
                    "Live · local heuristics refresh on this screen"
                } else {
                    "Realtime refresh paused"
                },
                style = MaterialTheme.typography.labelSmall,
                color = accent.copy(alpha = 0.85f)
            )
        }
    }
}

fun scoreAccent(score: Int?): androidx.compose.ui.graphics.Color {
    if (score == null) return ElectricTeal
    return when (GuardianScore.rankFor(score)) {
        GuardianRank.AEGIS -> SafeGreen
        GuardianRank.WARDED -> ElectricTeal
        GuardianRank.EXPOSED -> AttentionAmber
        GuardianRank.BREACHED -> HighRed
    }
}

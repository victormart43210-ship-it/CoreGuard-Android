package com.coldboar.coreguard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coldboar.coreguard.SecurityCheckState
import com.coldboar.coreguard.ui.theme.AttentionAmber
import com.coldboar.coreguard.ui.theme.HighRed
import com.coldboar.coreguard.ui.theme.SafeGreen

@Composable
fun SecurityStatusChip(state: SecurityCheckState, modifier: Modifier = Modifier) {
    val color = state.toStatusColor()
    Text(
        text = state.name,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

fun SecurityCheckState.toStatusColor(): Color = when (this) {
    SecurityCheckState.PASS -> SafeGreen
    SecurityCheckState.WARN -> AttentionAmber
    SecurityCheckState.FAIL -> HighRed
}

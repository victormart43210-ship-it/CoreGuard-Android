package com.coldboar.coreguard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.coldboar.coreguard.ui.theme.SurfaceGlass

/**
 * A frosted-glass styled card with a thin coloured top-edge accent line.
 */
@Composable
fun GlassCard(
    accentTint: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(SurfaceGlass)
            .border(0.8.dp, accentTint.copy(alpha = 0.25f), shape),
    ) {
        // Thin accent line along the top edge
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(accentTint.copy(alpha = 0.45f)),
        )
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            content()
        }
    }
}

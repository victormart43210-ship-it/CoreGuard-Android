package com.coldboar.coreguard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.coldboar.coreguard.ui.theme.SurfaceGlass

/**
 * A frosted-glass styled card with a subtle coloured top accent border.
 */
@Composable
fun GlassCard(
    accentTint: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(SurfaceGlass)
            .border(0.8.dp, accentTint.copy(alpha = 0.25f), shape),
    ) {
        // Accent top-edge highlight
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .background(
                    color = accentTint.copy(alpha = 0.08f),
                )
                .padding(top = 1.dp),
        )
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            content()
        }
    }
}

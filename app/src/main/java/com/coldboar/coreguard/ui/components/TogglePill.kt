package com.coldboar.coreguard.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.coldboar.coreguard.ui.theme.CyanPrimary
import com.coldboar.coreguard.ui.theme.CyanShadow
import com.coldboar.coreguard.ui.theme.SurfaceGlass
import com.coldboar.coreguard.ui.theme.TextLow

private val PillWidth = 44.dp
private val PillHeight = 24.dp
private val ThumbSize = 18.dp

/**
 * A compact animated toggle switch styled for the cyberpunk dark theme.
 */
@Composable
fun TogglePill(checked: Boolean, onChange: (Boolean) -> Unit) {
    val trackColor by animateColorAsState(
        targetValue = if (checked) CyanPrimary.copy(alpha = 0.35f) else SurfaceGlass,
        animationSpec = tween(200),
        label = "trackColor",
    )
    val borderColor by animateColorAsState(
        targetValue = if (checked) CyanPrimary else CyanShadow,
        animationSpec = tween(200),
        label = "borderColor",
    )
    val thumbColor by animateColorAsState(
        targetValue = if (checked) CyanPrimary else TextLow,
        animationSpec = tween(200),
        label = "thumbColor",
    )
    val thumbOffset by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(200),
        label = "thumbOffset",
    )

    Box(
        modifier = Modifier
            .width(PillWidth)
            .height(PillHeight)
            .clip(RoundedCornerShape(PillHeight / 2))
            .background(trackColor)
            .border(0.8.dp, borderColor, RoundedCornerShape(PillHeight / 2))
            .clickable { onChange(!checked) }
            .padding(horizontal = 3.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        val maxOffset = PillWidth - ThumbSize - 6.dp
        Box(
            modifier = Modifier
                .size(ThumbSize)
                .offset(x = maxOffset * thumbOffset)
                .clip(CircleShape)
                .background(thumbColor),
        )
    }
}

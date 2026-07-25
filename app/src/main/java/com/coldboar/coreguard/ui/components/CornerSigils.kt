package com.coldboar.coreguard.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.coldboar.coreguard.ui.theme.CyanPrimary
import com.coldboar.coreguard.ui.theme.CyanShadow

/**
 * Draws decorative L-shaped corner sigils in all four corners of the available space.
 * Intended to be placed inside a Box that fills the full screen.
 */
@Composable
fun CornerSigils() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val arm = 28.dp.toPx()
        val stroke = 1.4.dp.toPx()
        val glow = CyanPrimary.copy(alpha = 0.55f)
        val dim = CyanShadow.copy(alpha = 0.9f)
        val pad = 14.dp.toPx()

        // Top-left
        drawLine(glow, Offset(pad, pad), Offset(pad + arm, pad), stroke)
        drawLine(glow, Offset(pad, pad), Offset(pad, pad + arm), stroke)
        drawLine(dim, Offset(pad + arm + 6.dp.toPx(), pad), Offset(pad + arm + 14.dp.toPx(), pad), stroke * 0.6f)

        // Top-right
        drawLine(glow, Offset(size.width - pad, pad), Offset(size.width - pad - arm, pad), stroke)
        drawLine(glow, Offset(size.width - pad, pad), Offset(size.width - pad, pad + arm), stroke)
        drawLine(dim, Offset(size.width - pad - arm - 6.dp.toPx(), pad), Offset(size.width - pad - arm - 14.dp.toPx(), pad), stroke * 0.6f)

        // Bottom-left
        drawLine(glow, Offset(pad, size.height - pad), Offset(pad + arm, size.height - pad), stroke)
        drawLine(glow, Offset(pad, size.height - pad), Offset(pad, size.height - pad - arm), stroke)

        // Bottom-right
        drawLine(glow, Offset(size.width - pad, size.height - pad), Offset(size.width - pad - arm, size.height - pad), stroke)
        drawLine(glow, Offset(size.width - pad, size.height - pad), Offset(size.width - pad, size.height - pad - arm), stroke)
    }
}

package com.coldboar.coreguard.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.coldboar.coreguard.ui.theme.CyanPrimary

@Composable
fun CornerSigils(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val len = 28.dp.toPx()
        val stroke = 1.5f
        val col = CyanPrimary.copy(alpha = 0.4f)
        val w = size.width
        val h = size.height

        // Top-left
        drawLine(col, Offset(0f, len), Offset(0f, 0f), stroke)
        drawLine(col, Offset(0f, 0f), Offset(len, 0f), stroke)
        // Top-right
        drawLine(col, Offset(w - len, 0f), Offset(w, 0f), stroke)
        drawLine(col, Offset(w, 0f), Offset(w, len), stroke)
        // Bottom-left
        drawLine(col, Offset(0f, h - len), Offset(0f, h), stroke)
        drawLine(col, Offset(0f, h), Offset(len, h), stroke)
        // Bottom-right
        drawLine(col, Offset(w - len, h), Offset(w, h), stroke)
        drawLine(col, Offset(w, h - len), Offset(w, h), stroke)
    }
}

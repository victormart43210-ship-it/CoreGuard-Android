package com.coldboar.coreguard.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * A small all-caps section label rendered in the given accent colour.
 */
@Composable
fun SectionLabel(text: String, color: Color) {
    Text(
        text = text.uppercase(),
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
    )
}

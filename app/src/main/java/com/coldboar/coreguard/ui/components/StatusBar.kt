package com.coldboar.coreguard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coldboar.coreguard.ui.theme.AcidGreen
import com.coldboar.coreguard.ui.theme.CyanPrimary
import com.coldboar.coreguard.ui.theme.SurfaceLine
import com.coldboar.coreguard.ui.theme.TextMid

@Composable
fun StatusBar(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "COREGUARD",
            color = CyanPrimary.copy(alpha = 0.5f),
            fontSize = 9.sp,
            letterSpacing = 1.2.sp
        )
        Spacer(Modifier.weight(1f))
        // Battery indicator
        Box(
            modifier = Modifier
                .width(22.dp)
                .height(10.dp)
                .border(0.5.dp, TextMid, RoundedCornerShape(2.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.8f)
                    .background(AcidGreen, RoundedCornerShape(1.dp))
            )
        }
        // Battery cap nub
        Box(
            modifier = Modifier
                .padding(start = 1.dp)
                .width(2.dp)
                .height(5.dp)
                .background(TextMid, RoundedCornerShape(1.dp))
        )
    }
}

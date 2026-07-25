package com.coldboar.coreguard.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coldboar.coreguard.ui.theme.CyanPrimary
import com.coldboar.coreguard.ui.theme.TextLow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Minimal top status bar that displays the current time on the left and
 * simple signal/battery indicators on the right.
 */
@Composable
fun StatusBar() {
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(time, color = TextLow, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("▲▲▲", color = CyanPrimary, fontSize = 9.sp)
            Text("●", color = CyanPrimary, fontSize = 9.sp)
            Text("100%", color = TextLow, fontSize = 9.sp)
        }
    }
}

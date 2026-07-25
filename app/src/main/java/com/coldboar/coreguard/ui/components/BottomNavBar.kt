package com.coldboar.coreguard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coldboar.coreguard.ui.theme.CyanPrimary
import com.coldboar.coreguard.ui.theme.CyanShadow
import com.coldboar.coreguard.ui.theme.SurfaceGlass
import com.coldboar.coreguard.ui.theme.TextLow
import com.coldboar.coreguard.ui.theme.TextMid

/**
 * @param label Display label shown below the icon.
 * @param icon  Vector icon for the tab.
 * @param route Navigation route to invoke when this tab is selected.
 */
data class NavTab(val label: String, val icon: ImageVector, val route: String)

/**
 * @param onSelect Called with the [NavTab.route] of the tapped tab.
 */
@Composable
fun BottomNavBar(
    tabs: List<NavTab>,
    selectedIndex: Int,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(SurfaceGlass)
            .border(
                width = 0.6.dp,
                color = CyanShadow,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            )
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { index, tab ->
            val selected = index == selectedIndex
            Column(
                modifier = Modifier
                    .clickable { onSelect(tab.route) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = tab.label,
                    tint = if (selected) CyanPrimary else TextLow,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = tab.label,
                    fontSize = 9.sp,
                    color = if (selected) CyanPrimary else TextMid,
                )
            }
        }
    }
}

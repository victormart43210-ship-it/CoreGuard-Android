package com.coldboar.coreguard.ui.components

import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.coldboar.coreguard.ui.theme.AbyssBlack
import com.coldboar.coreguard.ui.theme.AcidGreen
import com.coldboar.coreguard.ui.theme.SurfaceLine
import com.coldboar.coreguard.ui.theme.TextMid

@Composable
fun TogglePill(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = AbyssBlack,
            checkedTrackColor = AcidGreen,
            uncheckedThumbColor = TextMid,
            uncheckedTrackColor = SurfaceLine
        )
    )
}

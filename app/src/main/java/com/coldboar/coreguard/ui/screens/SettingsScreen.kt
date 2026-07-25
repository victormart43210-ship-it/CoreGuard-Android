package com.coldboar.coreguard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coldboar.coreguard.ui.components.BottomNavBar
import com.coldboar.coreguard.ui.components.BoarEmblem
import com.coldboar.coreguard.ui.components.CornerSigils
import com.coldboar.coreguard.ui.components.GlassCard
import com.coldboar.coreguard.ui.components.GlowActionButton
import com.coldboar.coreguard.ui.components.NavTab
import com.coldboar.coreguard.ui.components.SectionLabel
import com.coldboar.coreguard.ui.components.StatusBar
import com.coldboar.coreguard.ui.components.TogglePill
import com.coldboar.coreguard.ui.nav.Routes
import com.coldboar.coreguard.ui.theme.AbyssBlack
import com.coldboar.coreguard.ui.theme.AcidGreen
import com.coldboar.coreguard.ui.theme.CrimsonDanger
import com.coldboar.coreguard.ui.theme.CrimsonGlow
import com.coldboar.coreguard.ui.theme.CyanPrimary
import com.coldboar.coreguard.ui.theme.CyanShadow
import com.coldboar.coreguard.ui.theme.CyanVibrant
import com.coldboar.coreguard.ui.theme.SurfaceGlass
import com.coldboar.coreguard.ui.theme.TextHigh
import com.coldboar.coreguard.ui.theme.TextLow
import com.coldboar.coreguard.ui.theme.TextMid

@Composable
fun SettingsScreen(onTab: (String) -> Unit) {
    var network by remember { mutableStateOf(true) }
    var storage by remember { mutableStateOf(true) }
    var notifications by remember { mutableStateOf(true) }
    var microphone by remember { mutableStateOf(false) }
    var biometric by remember { mutableStateOf(true) }

    Box(Modifier.fillMaxSize().background(AbyssBlack)) {
        CornerSigils()
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
            StatusBar()
            Spacer(Modifier.height(8.dp))
            Text("Settings", color = TextHigh, fontSize = 22.sp, fontWeight = FontWeight.Bold)

            Spacer(Modifier.height(12.dp))
            GlassCard(accentTint = CyanPrimary) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BoarEmblem(sizeDp = 64.dp, withRunes = false)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            "Coldboar User", color = TextHigh,
                            fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceGlass)
                                .border(0.6.dp, CyanShadow, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text("Free Plan", color = TextMid, fontSize = 10.sp)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Brush.horizontalGradient(listOf(CyanVibrant, AcidGreen)))
                            .clickable { onTab(Routes.PREMIUM) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Upgrade", color = AbyssBlack, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            GlassCard(accentTint = CyanPrimary) {
                SectionLabel("Permissions", color = CyanPrimary)
                Spacer(Modifier.height(6.dp))
                PermRow("Network access",  network)       { network = it }
                PermRow("Storage access",  storage)       { storage = it }
                PermRow("Notifications",   notifications) { notifications = it }
                PermRow("Microphone",      microphone)    { microphone = it }
                PermRow("Biometric lock",  biometric)     { biometric = it }
            }

            Spacer(Modifier.height(14.dp))
            GlassCard(accentTint = CyanPrimary) {
                SectionLabel("About", color = CyanPrimary)
                Spacer(Modifier.height(6.dp))
                AboutRow("Version 1.0.0")
                AboutRow("Privacy policy")
                AboutRow("Open source licenses")
            }

            Spacer(Modifier.height(14.dp))
            GlowActionButton(
                "Sign Out",
                accent = Brush.horizontalGradient(listOf(CrimsonDanger, CrimsonGlow))
            ) { }

            Spacer(Modifier.weight(1f))
            BottomNavBar(
                tabs = listOf(
                    NavTab("Home",     Icons.Filled.Home),
                    NavTab("Explore",  Icons.Filled.Explore),
                    NavTab("Create",   Icons.Filled.Add),
                    NavTab("Chats",    Icons.Filled.Chat),
                    NavTab("Settings", Icons.Filled.Settings),
                ),
                selectedIndex = 4,
                onSelect = { onTab(Routes.SETTINGS) }
            )
        }
    }
}

@Composable
private fun PermRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextHigh, fontSize = 13.sp)
        Spacer(Modifier.weight(1f))
        TogglePill(checked = checked, onChange = onChange)
    }
}

@Composable
private fun AboutRow(text: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, color = TextHigh, fontSize = 13.sp)
        Spacer(Modifier.weight(1f))
        Icon(
            Icons.Filled.ChevronRight, null, tint = TextLow,
            modifier = Modifier.size(18.dp)
        )
    }
}

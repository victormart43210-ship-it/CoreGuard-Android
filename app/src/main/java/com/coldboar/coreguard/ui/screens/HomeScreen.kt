package com.coldboar.coreguard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coldboar.coreguard.ui.components.BottomNavBar
import com.coldboar.coreguard.ui.components.CornerSigils
import com.coldboar.coreguard.ui.components.CpuBar
import com.coldboar.coreguard.ui.components.GlassCard
import com.coldboar.coreguard.ui.components.NavTab
import com.coldboar.coreguard.ui.components.NeonCircularGauge
import com.coldboar.coreguard.ui.components.QuickActionPill
import com.coldboar.coreguard.ui.components.SectionLabel
import com.coldboar.coreguard.ui.components.TogglePill
import com.coldboar.coreguard.ui.nav.Routes
import com.coldboar.coreguard.ui.theme.AcidGreen
import com.coldboar.coreguard.ui.theme.AbyssBlack
import com.coldboar.coreguard.ui.theme.TextHigh

@Composable
fun HomeScreen(
    onTab: (String) -> Unit,
    onAction: (String) -> Unit
) {
    Box(Modifier.fillMaxSize().background(AbyssBlack)) {
        CornerSigils()
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                "CoreGuard",
                color = TextHigh, fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(16.dp))
            GlassCard {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    NeonCircularGauge(percent = 67f)
                }
            }

            Spacer(Modifier.height(16.dp))
            GlassCard(accentTint = AcidGreen) {
                CpuBar(value = 45)
            }

            Spacer(Modifier.height(16.dp))
            GlassCard(accentTint = AcidGreen) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(AcidGreen, CircleShape))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "Real-time Protection",
                            color = TextHigh, fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "ACTIVE",
                            color = AcidGreen, fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    TogglePill(checked = true, onChange = { })
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel("Quick Actions")
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickActionPill("Scan",        Icons.Filled.Search,  modifier = Modifier.weight(1f)) { onAction("Scan") }
                QuickActionPill("Reports",     Icons.Filled.Shield,  modifier = Modifier.weight(1f)) { onAction("Reports") }
                QuickActionPill("Permissions", Icons.Filled.Lock,    modifier = Modifier.weight(1f)) { onAction("Permissions") }
                QuickActionPill("Optimize",    Icons.Filled.Bolt,    modifier = Modifier.weight(1f)) { onAction("Optimize") }
            }

            Spacer(Modifier.height(40.dp))
        }

        BottomNavBar(
            tabs = listOf(
                NavTab("Home",     Icons.Filled.Home),
                NavTab("Security", Icons.Filled.Security),
                NavTab("Alerts",   Icons.Filled.Notifications),
                NavTab("Tools",    Icons.Filled.Build),
                NavTab("Profile",  Icons.Filled.Person),
            ),
            selectedIndex = 0,
            onSelect = { onTab(Routes.HOME) },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

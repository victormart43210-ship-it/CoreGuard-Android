package com.coldboar.coreguard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.coldboar.coreguard.ui.nav.Routes
import com.coldboar.coreguard.ui.theme.AbyssBlack
import com.coldboar.coreguard.ui.theme.AcidGreen
import com.coldboar.coreguard.ui.theme.CyanPrimary
import com.coldboar.coreguard.ui.theme.CyanShadow
import com.coldboar.coreguard.ui.theme.CyanVibrant
import com.coldboar.coreguard.ui.theme.SurfaceGlass
import com.coldboar.coreguard.ui.theme.SurfaceNight
import com.coldboar.coreguard.ui.theme.TextHigh
import com.coldboar.coreguard.ui.theme.TextLow
import com.coldboar.coreguard.ui.theme.TextMid

@Composable
fun PremiumScreen(onTab: (String) -> Unit) {
    Box(Modifier.fillMaxSize().background(AbyssBlack)) {
        CornerSigils()
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "CoreGuard Elite", color = TextHigh, fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(SurfaceGlass)
                        .border(0.6.dp, CyanShadow, RoundedCornerShape(17.dp))
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Close, "Close", tint = TextMid,
                        modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                BoarEmblem(sizeDp = 160.dp)
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Unlock Advanced Protection",
                color = TextHigh, fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(18.dp))
            GlassCard(accentTint = AcidGreen) {
                FeatureRow("Real-time CPU monitoring")
                FeatureRow("Advanced threat detection")
                FeatureRow("Network analysis")
                FeatureRow("Privacy audit & leak alerts")
                FeatureRow("Priority support 24/7")
            }

            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PriceTile(
                    "Monthly", "$4.99", "Billed monthly",
                    highlighted = false, badge = null,
                    modifier = Modifier.weight(1f)
                )
                PriceTile(
                    "Yearly", "$39.99", "Billed yearly",
                    highlighted = true, badge = "SAVE 33%",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(16.dp))
            GlowActionButton(
                "Upgrade Now",
                accent = Brush.horizontalGradient(listOf(CyanVibrant, AcidGreen))
            ) { }
            Spacer(Modifier.height(8.dp))
            Text(
                "Cancel anytime — 7-day free trial",
                color = TextLow, fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.weight(1f))
            BottomNavBar(
                tabs = listOf(
                    NavTab("Home",        Icons.Filled.Home,     Routes.HOME),
                    NavTab("Security",    Icons.Filled.Security, Routes.SHIELD),
                    NavTab("Performance", Icons.Filled.BarChart, Routes.SCANNER),
                    NavTab("Premium",     Icons.Filled.Star,     Routes.PREMIUM),
                    NavTab("Profile",     Icons.Filled.Person,   Routes.SETTINGS),
                ),
                selectedIndex = 3,
                onSelect = onTab
            )
        }
    }
}

@Composable
private fun FeatureRow(text: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(CyanPrimary.copy(alpha = 0.15f))
                .border(0.7.dp, CyanPrimary, RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("✓", color = CyanPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Text(text, color = TextHigh, fontSize = 13.sp)
    }
}

@Composable
private fun PriceTile(
    label: String, price: String, billedText: String,
    highlighted: Boolean, badge: String?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (highlighted) SurfaceGlass else SurfaceNight)
            .border(
                width = if (highlighted) 1.4.dp else 0.8.dp,
                color = if (highlighted) AcidGreen else CyanShadow,
                shape = RoundedCornerShape(20.dp)
            )
            .padding(14.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = TextHigh, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                if (badge != null) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(AcidGreen.copy(alpha = 0.15f))
                            .border(0.6.dp, AcidGreen, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(badge, color = AcidGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                price,
                color = if (highlighted) AcidGreen else TextHigh,
                fontSize = 18.sp, fontWeight = FontWeight.Bold
            )
            Text(billedText, color = TextMid, fontSize = 10.sp)
        }
    }
}

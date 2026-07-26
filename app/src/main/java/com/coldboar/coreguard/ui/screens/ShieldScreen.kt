package com.coldboar.coreguard.ui.screens

import android.app.Activity
import android.net.VpnService
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coldboar.coreguard.mvt.NemesisShield
import com.coldboar.coreguard.mvt.ShieldState
import com.coldboar.coreguard.ui.components.AtmosphereBackground
import com.coldboar.coreguard.ui.theme.AttentionAmber
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.MutedText
import com.coldboar.coreguard.ui.theme.SafeGreen
import com.coldboar.coreguard.ui.theme.SurfacePewter

@Composable
fun ShieldScreen() {
    val context = LocalContext.current

    var shieldActive by remember { mutableStateOf(ShieldState.isActive) }
    var totalBlocked by remember { mutableStateOf(ShieldState.totalBlocked) }

    DisposableEffect(Unit) {
        val listener = ShieldState.Listener {
            shieldActive = ShieldState.isActive
            totalBlocked = ShieldState.totalBlocked
        }
        ShieldState.addListener(listener)
        onDispose { ShieldState.removeListener(listener) }
    }

    val vpnConsentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            NemesisShield.start(context)
        } else {
            shieldActive = false
            Toast.makeText(context, "Privacy Shield needs VPN permission to run.", Toast.LENGTH_SHORT).show()
        }
    }

    AtmosphereBackground(accent = if (shieldActive) SafeGreen else ElectricTeal) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text(
                text = "Privacy Shield",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = MaterialTheme.typography.headlineLarge.fontSize
                ),
                color = ElectricTeal,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Blocks connections to servers known to track or surveil — private, on-device VPN.",
                style = MaterialTheme.typography.bodyMedium,
                color = MutedText
            )

            Spacer(modifier = Modifier.height(28.dp))

            ShieldPresence(active = shieldActive, blocked = totalBlocked)

            Spacer(modifier = Modifier.height(22.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfacePewter.copy(alpha = 0.9f))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (shieldActive) "Shield armed" else "Shield idle",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (shieldActive) {
                            "$totalBlocked surveillance domains blocked"
                        } else {
                            "Tap to request VPN permission and arm"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (shieldActive) SafeGreen else MutedText
                    )
                }
                Switch(
                    checked = shieldActive,
                    onCheckedChange = { enabled ->
                        if (enabled && !ShieldState.isActive) {
                            val prepare = VpnService.prepare(context)
                            if (prepare != null) {
                                vpnConsentLauncher.launch(prepare)
                            } else {
                                NemesisShield.start(context)
                            }
                        } else if (!enabled && ShieldState.isActive) {
                            NemesisShield.stop(context)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ElectricTeal,
                        checkedTrackColor = ElectricTeal.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.semantics {
                        contentDescription = if (shieldActive) "Privacy Shield on" else "Privacy Shield off"
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfacePewter.copy(alpha = 0.7f))
                    .padding(16.dp)
            ) {
                Text("Honest limits", style = MaterialTheme.typography.titleMedium, color = AttentionAmber)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• Only blocks domains with a matching IOC indicator.\n" +
                        "• Cannot block traffic that uses a hardcoded IP (no DNS lookup).\n" +
                        "• Requires explicit VPN permission from Android.\n" +
                        "• Full traffic routing is not yet implemented in this build.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText
                )
            }
        }
    }
}

@Composable
private fun ShieldPresence(active: Boolean, blocked: Int) {
    val transition = rememberInfiniteTransition(label = "shield")
    val pulse by transition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (active) 1800 else 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val ring by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring"
    )

    val accent = if (active) SafeGreen else ElectricTeal

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(200.dp)) {
            val r = size.minDimension / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accent.copy(alpha = if (active) 0.28f * pulse else 0.1f),
                        Color.Transparent
                    )
                ),
                radius = r
            )
            drawCircle(
                color = accent.copy(alpha = 0.35f),
                radius = r * 0.62f,
                style = Stroke(width = 3.dp.toPx())
            )
            if (active) {
                drawArc(
                    color = accent.copy(alpha = 0.7f),
                    startAngle = ring,
                    sweepAngle = 48f,
                    useCenter = false,
                    style = Stroke(width = 4.dp.toPx())
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (active) "ARMED" else "STANDBY",
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 3.sp),
                color = accent,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (active) "$blocked blocked" else "Awaiting arm",
                style = MaterialTheme.typography.bodySmall,
                color = MutedText
            )
        }
    }
}

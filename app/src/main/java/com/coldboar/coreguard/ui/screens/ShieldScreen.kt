package com.coldboar.coreguard.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.coldboar.coreguard.mvt.NemesisShield
import com.coldboar.coreguard.mvt.ShieldState
import com.coldboar.coreguard.ui.components.ScreenAtmosphere
import com.coldboar.coreguard.ui.components.ScreenHeader
import com.coldboar.coreguard.ui.components.TechStatusChip
import com.coldboar.coreguard.ui.theme.AttentionAmber
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.MutedText
import com.coldboar.coreguard.ui.theme.RestrainedGold
import com.coldboar.coreguard.ui.theme.SafeGreen

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

    fun launchVpnConsentOrStart() {
        val prepare = VpnService.prepare(context)
        if (prepare != null) {
            vpnConsentLauncher.launch(prepare)
        } else {
            NemesisShield.start(context)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // Notification permission is recommended for the FGS status, not a hard blocker.
        launchVpnConsentOrStart()
    }

    fun requestEnableShield() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        launchVpnConsentOrStart()
    }

    ScreenAtmosphere(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        accent = if (shieldActive) SafeGreen else ElectricTeal
    ) {
        ScreenHeader(
            title = "Privacy Shield",
            subtitle = "On-device DNS filter VPN that can block domains matching known surveillance / tracker indicators.",
            eyebrow = if (shieldActive) "Perimeter armed" else "Perimeter standby"
        )

        Spacer(modifier = Modifier.height(16.dp))
        ShieldPresence(active = shieldActive, blocked = totalBlocked)
        Spacer(modifier = Modifier.height(12.dp))
        TechStatusChip(
            text = if (shieldActive) "Armed · $totalBlocked blocked" else "Standby · awaiting arm",
            color = if (shieldActive) SafeGreen else ElectricTeal,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Activate My Privacy Shield",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = shieldActive,
                        onCheckedChange = { enabled ->
                            if (enabled && !ShieldState.isActive) {
                                requestEnableShield()
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

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (shieldActive) "On · $totalBlocked blocked" else "Off",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (shieldActive) SafeGreen else AttentionAmber
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("How it works", style = MaterialTheme.typography.titleMedium, color = ElectricTeal)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• Matches DNS lookups against on-device IOC / block indicators.\n" +
                        "• Blocked domains get a local NXDOMAIN-style response.\n" +
                        "• Allowed DNS queries are forwarded to your system resolver " +
                        "(or 8.8.8.8 if none is available).\n" +
                        "• Requires explicit Android VPN permission.\n" +
                        "• Does not inspect full app traffic payloads or guarantee spyware removal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText
                )
            }
        }
    }
}

@Composable
private fun ShieldPresence(active: Boolean, blocked: Int) {
    val transition = rememberInfiniteTransition(label = "shieldPresence")
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
            .height(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(180.dp)) {
            val r = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
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
            drawCircle(
                color = accent.copy(alpha = 0.18f),
                radius = r * 0.78f,
                style = Stroke(width = 1.5.dp.toPx())
            )
            val ticks = 36
            for (i in 0 until ticks) {
                val deg = Math.toRadians(i * 360.0 / ticks + ring * 0.15 - 90.0)
                val c = kotlin.math.cos(deg).toFloat()
                val s = kotlin.math.sin(deg).toFloat()
                val major = i % 3 == 0
                val inner = r * (if (major) 0.8f else 0.86f)
                val outer = r * 0.94f
                drawLine(
                    color = accent.copy(alpha = if (active) (if (major) 0.55f else 0.28f) * pulse else 0.16f),
                    start = Offset(center.x + c * inner, center.y + s * inner),
                    end = Offset(center.x + c * outer, center.y + s * outer),
                    strokeWidth = if (major) 2.1f else 1.3f
                )
            }
            // Hex lock geometry when armed
            if (active) {
                val hexR = r * 0.42f
                val path = androidx.compose.ui.graphics.Path()
                for (i in 0..6) {
                    val deg = Math.toRadians(i * 60.0 - 90.0 + ring * 0.05)
                    val x = center.x + kotlin.math.cos(deg).toFloat() * hexR
                    val y = center.y + kotlin.math.sin(deg).toFloat() * hexR
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color = RestrainedGold.copy(alpha = 0.35f), style = Stroke(width = 1.6.dp.toPx()))
                drawArc(
                    color = accent.copy(alpha = 0.75f),
                    startAngle = ring,
                    sweepAngle = 52f,
                    useCenter = false,
                    style = Stroke(width = 4.dp.toPx())
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (active) "ARMED" else "STANDBY",
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 3.4.sp),
                color = accent,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (active) "$blocked BLOCKED" else "AWAITING ARM",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.6.sp),
                color = MutedText
            )
        }
    }
}

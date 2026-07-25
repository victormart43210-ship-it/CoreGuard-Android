package com.coldboar.coreguard.ui.screens

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.coldboar.coreguard.mvt.NemesisShield
import com.coldboar.coreguard.mvt.ShieldState
import com.coldboar.coreguard.ui.components.CoreGuardCard
import com.coldboar.coreguard.ui.components.PrimaryTealButton
import com.coldboar.coreguard.ui.components.ScreenAtmosphere
import com.coldboar.coreguard.ui.components.ScreenHeader
import com.coldboar.coreguard.ui.theme.AttentionAmber
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.HighRed
import com.coldboar.coreguard.ui.theme.MutedText
import com.coldboar.coreguard.ui.theme.SafeGreen

@Composable
fun ShieldScreen() {
    val context = LocalContext.current

    var shieldActive by remember { mutableStateOf(ShieldState.isActive) }
    var totalBlocked by remember { mutableStateOf(ShieldState.totalBlocked) }
    var pendingConsent by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val listener = ShieldState.Listener {
            shieldActive = ShieldState.isActive
            totalBlocked = ShieldState.totalBlocked
            if (ShieldState.isActive) {
                pendingConsent = false
                permissionDenied = false
            }
        }
        ShieldState.addListener(listener)
        onDispose { ShieldState.removeListener(listener) }
    }

    val vpnConsentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        pendingConsent = false
        if (result.resultCode == Activity.RESULT_OK) {
            permissionDenied = false
            NemesisShield.start(context)
        } else {
            shieldActive = false
            permissionDenied = true
        }
    }

    fun requestShieldOn() {
        permissionDenied = false
        val prepare = VpnService.prepare(context)
        if (prepare != null) {
            pendingConsent = true
            vpnConsentLauncher.launch(prepare)
        } else {
            NemesisShield.start(context)
        }
    }

    val statusColor by animateColorAsState(
        targetValue = when {
            permissionDenied -> HighRed
            pendingConsent -> AttentionAmber
            shieldActive -> SafeGreen
            else -> MutedText
        },
        label = "shieldStatusColor"
    )

    ScreenAtmosphere(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        ScreenHeader(
            title = "DNS protection",
            subtitle = "Blocks known tracking and surveillance domains when apps look them up by name."
        )

        Spacer(Modifier.height(20.dp))

        CoreGuardCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Turn on DNS protection",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Android will ask for VPN permission. CoreGuard uses it only on this device — traffic is not sent to us.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedText
                    )
                }
                Switch(
                    checked = shieldActive,
                    enabled = !pendingConsent,
                    onCheckedChange = { enabled ->
                        if (enabled && !ShieldState.isActive) {
                            requestShieldOn()
                        } else if (!enabled && ShieldState.isActive) {
                            NemesisShield.stop(context)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ElectricTeal,
                        checkedTrackColor = ElectricTeal.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.semantics {
                        contentDescription =
                            if (shieldActive) "DNS protection on" else "DNS protection off"
                    }
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = when {
                    permissionDenied -> "Permission denied — protection stays off"
                    pendingConsent -> "Waiting for Android VPN permission…"
                    shieldActive -> "On · $totalBlocked domains blocked"
                    else -> "Off"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = statusColor
            )
        }

        if (permissionDenied) {
            Spacer(Modifier.height(12.dp))
            CoreGuardCard(containerColor = HighRed.copy(alpha = 0.12f)) {
                Text(
                    text = "VPN permission needed",
                    style = MaterialTheme.typography.titleSmall,
                    color = HighRed
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Without Android’s VPN permission, DNS protection can’t run. You can try again anytime.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText
                )
                Spacer(Modifier.height(12.dp))
                PrimaryTealButton(
                    text = "Try again",
                    onClick = { requestShieldOn() }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        CoreGuardCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
            Text("What this protects", style = MaterialTheme.typography.titleMedium, color = ElectricTeal)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "• Blocks known tracking domains when apps look them up by name.\n" +
                    "• Cannot block apps that connect with a fixed IP address (no name lookup).\n" +
                    "• Needs your explicit VPN permission from Android.\n" +
                    "• Filters DNS on this device only — it is not a full traffic VPN yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MutedText
            )
        }
    }
}

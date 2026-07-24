package com.coldboar.coreguard.ui.screens

import android.app.Activity
import android.net.VpnService
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.coldboar.coreguard.mvt.NemesisShield
import com.coldboar.coreguard.mvt.ShieldState
import com.coldboar.coreguard.ui.theme.AttentionAmber
import com.coldboar.coreguard.ui.theme.ElectricTeal
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Privacy Shield",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = "Blocks connections to servers known to track or surveil, using a private on-device VPN.",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Enable Privacy Shield",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
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

                Spacer(Modifier.height(8.dp))

                Text(
                    text = if (shieldActive) "On · $totalBlocked blocked" else "Off",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (shieldActive) SafeGreen else AttentionAmber
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Limitations", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "• Only blocks domains with a matching IOC indicator.\n" +
                        "• Cannot block traffic that uses a hardcoded IP address (no DNS lookup).\n" +
                        "• Requires explicit VPN permission from the operating system.\n" +
                        "• Full traffic routing is not yet implemented in this build.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

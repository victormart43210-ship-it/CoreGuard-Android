package com.coldboar.coreguard.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.coldboar.coreguard.mvt.NemesisShield
import com.coldboar.coreguard.mvt.ShieldState
import com.coldboar.coreguard.ui.theme.AttentionAmber
import com.coldboar.coreguard.ui.theme.ElectricTeal
import com.coldboar.coreguard.ui.theme.MutedText
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Privacy Shield",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = "On-device DNS filter VPN that can block domains matching known surveillance / tracker indicators.",
            style = MaterialTheme.typography.bodyMedium,
            color = MutedText
        )

        Spacer(modifier = Modifier.height(20.dp))

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

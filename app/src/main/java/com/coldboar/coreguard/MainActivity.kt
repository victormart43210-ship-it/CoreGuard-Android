package com.coldboar.coreguard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.coldboar.coreguard.ui.nav.CoreGuardNavGraph
import com.coldboar.coreguard.ui.theme.CoreGuardTheme

/**
 * Single launcher Activity for the entire app.
 *
 * Sets up the Compose content tree:
 *   MainActivity → CoreGuardTheme → CoreGuardNavGraph → one NavHost
 *
 * All screen navigation is handled inside [CoreGuardNavGraph]. This Activity
 * contains no polling logic, no ViewBinding, and no direct navigation calls.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CoreGuardTheme {
                CoreGuardNavGraph()
            }
        }
    }
}

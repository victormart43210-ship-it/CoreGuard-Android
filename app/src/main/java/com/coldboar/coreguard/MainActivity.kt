package com.coldboar.coreguard

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.coldboar.coreguard.ui.CoreGuardApp
import com.coldboar.coreguard.ui.theme.CoreGuardTheme

/**
 * Single launcher Activity for the entire app.
 *
 * Sets up the Compose content tree:
 *   MainActivity → CoreGuardTheme → CoreGuardApp → one NavHost
 *
 * All screen navigation is handled inside [CoreGuardApp]. This Activity
 * contains no polling logic, no ViewBinding, and no direct navigation calls.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CoreGuardTheme {
                CoreGuardApp()
            }
        }
    }
}

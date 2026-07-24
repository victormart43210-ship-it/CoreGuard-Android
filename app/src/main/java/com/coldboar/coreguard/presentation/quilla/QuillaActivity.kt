package com.coldboar.coreguard.presentation.quilla

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Full-screen entry point for the Quilla Intelligence Dashboard.
 * Marked with [@AndroidEntryPoint] so Hilt can inject the [QuillaProfileViewModel].
 */
@AndroidEntryPoint
class QuillaActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                QuillaProfileScreen(
                    onNavigateUp = { finish() }
                )
            }
        }
    }
}

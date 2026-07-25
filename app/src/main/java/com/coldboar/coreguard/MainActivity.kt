package com.coldboar.coreguard

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateOf
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
 *
 * Key combination: **Shift + Alt + S** toggles the hidden [SecretPortalScreen] overlay,
 * mirroring the web-layer secret-portal toggle pattern.
 */
class MainActivity : AppCompatActivity() {

    /** Shared toggle state for the secret-portal overlay. */
    private val secretPortalVisible = mutableStateOf(false)

    private val billingProvider: PlayBillingProvider
        get() = CoreGuardApplication.require().billingProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CoreGuardTheme {
                CoreGuardApp(
                    secretPortalVisible = secretPortalVisible,
                    billingProvider = billingProvider
                )
            }
        }
        billingProvider.attach(this)
    }

    override fun onResume() {
        super.onResume()
        billingProvider.attach(this)
    }

    override fun onDestroy() {
        billingProvider.detach()
        super.onDestroy()
    }

    /**
     * Intercepts **Shift + Alt + S** to toggle the secret-portal overlay.
     *
     * Equivalent to the web handler:
     * ```js
     * if (event.shiftKey && event.altKey && event.code === 'KeyS') { … }
     * ```
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_S && event.isShiftPressed && event.isAltPressed) {
            val opening = !secretPortalVisible.value
            secretPortalVisible.value = opening
            if (opening) {
                Log.d(TAG, "The ritual is complete. The vault has shifted.")
            } else {
                Log.d(TAG, "The vault has been sealed.")
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private companion object {
        const val TAG = "CoreGuard"
    }
}

package com.coldboar.coreguard.attestation

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Outcome of a biometric step-up authentication attempt.
 */
sealed class BiometricStepUpResult {
    /** The user authenticated successfully. */
    object Success : BiometricStepUpResult()

    /** The user cancelled or dismissed the prompt. */
    object Cancelled : BiometricStepUpResult()

    /** Authentication failed (e.g. too many failed attempts, hardware error). */
    data class Error(val code: Int, val message: String) : BiometricStepUpResult()

    /** No suitable biometric hardware / enrolment found. */
    data class Unavailable(val reason: String) : BiometricStepUpResult()
}

/**
 * Manages biometric step-up re-authentication for critical operations such as
 * exporting encryption keys or viewing raw security telemetry.
 *
 * Wrap any sensitive UI action in [authenticate]:
 * ```kotlin
 * BiometricStepUpManager.authenticate(
 *     activity = this,
 *     title = "Confirm Export",
 *     subtitle = "Authenticate to export encryption keys",
 *     onResult = { result ->
 *         if (result is BiometricStepUpResult.Success) performExport()
 *     }
 * )
 * ```
 */
object BiometricStepUpManager {

    /**
     * Returns a [BiometricReadiness] describing whether the device can perform
     * biometric authentication right now.
     */
    fun checkReadiness(context: Context): BiometricReadiness {
        val manager = BiometricManager.from(context)
        return when (manager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricReadiness.Ready
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricReadiness.NoHardware
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricReadiness.HardwareUnavailable
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricReadiness.NoneEnrolled
            else -> BiometricReadiness.Unknown
        }
    }

    /**
     * Shows a [BiometricPrompt] and invokes [onResult] with the outcome.
     *
     * Must be called from the main thread; [activity] must be in the RESUMED
     * state. [onResult] is also called on the main thread.
     *
     * @param activity  The host [FragmentActivity].
     * @param title     Prompt dialog title.
     * @param subtitle  Prompt dialog subtitle (displayed below the title).
     * @param onResult  Callback receiving the authentication outcome.
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String = "Verify Identity",
        subtitle: String = "Authentication required to proceed",
        onResult: (BiometricStepUpResult) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onResult(BiometricStepUpResult.Success)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                ) {
                    onResult(BiometricStepUpResult.Cancelled)
                } else {
                    onResult(BiometricStepUpResult.Error(errorCode, errString.toString()))
                }
            }

            override fun onAuthenticationFailed() {
                // Individual failed attempt – prompt stays open, wait for next try.
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        prompt.authenticate(promptInfo)
    }
}

/** Describes whether the device can perform biometric step-up authentication. */
enum class BiometricReadiness {
    Ready,
    NoHardware,
    HardwareUnavailable,
    NoneEnrolled,
    Unknown
}

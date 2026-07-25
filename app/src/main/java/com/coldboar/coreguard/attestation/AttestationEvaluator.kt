package com.coldboar.coreguard.attestation

import com.coldboar.coreguard.SecurityCheckEvaluator
import com.coldboar.coreguard.SecurityCheckResult
import com.coldboar.coreguard.SecurityCheckState

/**
 * A [SecurityCheckEvaluator] that surfaces the result of the most recent Play
 * Integrity attestation as a standard [SecurityCheckResult].
 *
 * Because actual attestation is asynchronous (and requires a network call),
 * this evaluator works with a pre-computed [AttestationResult] that is updated
 * after each attestation cycle. Inject a cached result for unit testing.
 *
 * @param lastResult Lambda returning the most recent attestation result.
 *                   Defaults to [AttestationResult.Unavailable] so the check
 *                   degrades gracefully on devices without Play Services.
 */
class AttestationEvaluator(
    private val lastResult: () -> AttestationResult = { AttestationResult.Unavailable }
) : SecurityCheckEvaluator {

    override fun evaluate(): SecurityCheckResult = when (val result = lastResult()) {
        is AttestationResult.Success -> {
            val strong = result.verdicts.contains(IntegrityVerdicts.MEETS_STRONG_INTEGRITY)
            val basic = result.verdicts.contains(IntegrityVerdicts.MEETS_BASIC_INTEGRITY)
            when {
                strong -> SecurityCheckResult(
                    id = "play_integrity",
                    displayName = "App Integrity",
                    state = SecurityCheckState.PASS,
                    explanation = "Play Integrity: MEETS_STRONG_INTEGRITY. App and device are unmodified."
                )
                basic -> SecurityCheckResult(
                    id = "play_integrity",
                    displayName = "App Integrity",
                    state = SecurityCheckState.WARN,
                    explanation = "Play Integrity: MEETS_BASIC_INTEGRITY only. Device may be unlocked or modified."
                )
                else -> SecurityCheckResult(
                    id = "play_integrity",
                    displayName = "App Integrity",
                    state = SecurityCheckState.FAIL,
                    explanation = "Play Integrity returned no positive verdicts. Device integrity cannot be confirmed."
                )
            }
        }
        is AttestationResult.Failure -> SecurityCheckResult(
            id = "play_integrity",
            displayName = "App Integrity",
            state = SecurityCheckState.WARN,
            explanation = "Play Integrity attestation failed: ${result.reason}"
        )
        AttestationResult.Unavailable -> SecurityCheckResult(
            id = "play_integrity",
            displayName = "App Integrity",
            state = SecurityCheckState.WARN,
            explanation = "Play Integrity API is not available on this device or environment."
        )
    }
}

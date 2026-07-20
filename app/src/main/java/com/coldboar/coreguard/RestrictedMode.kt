package com.coldboar.coreguard

/**
 * Prototype "restricted mode" — a local policy that tightens feature access when
 * high-risk security checks FAIL.
 *
 * This is **not** a hardened TEE / Play Integrity guarantee. Advanced attackers
 * can bypass client-side gates. Claims remain prototype-grade.
 */
data class RestrictedModeDecision(
    val active: Boolean,
    val reasons: List<String>
) {
    val summary: String
        get() = if (!active) {
            "Restricted mode off"
        } else {
            "Restricted mode on: ${reasons.joinToString("; ")}"
        }
}

/**
 * Evaluates whether the app should enter restricted mode based on security
 * check results.
 *
 * Rules (prototype):
 * - Any [SecurityCheckState.FAIL] activates restricted mode.
 * - WARN alone does not activate restricted mode (e.g. emulator / debug build).
 */
object RestrictedMode {
    fun evaluate(results: List<SecurityCheckResult>): RestrictedModeDecision {
        val failReasons = results
            .filter { it.state == SecurityCheckState.FAIL }
            .map { "${it.displayName}: ${it.explanation}" }
        return RestrictedModeDecision(
            active = failReasons.isNotEmpty(),
            reasons = failReasons
        )
    }

    /**
     * Feature gates under restricted mode.
     * Premium entitlement alone cannot override a FAIL-driven restriction.
     */
    fun canExportReport(isPremium: Boolean, restricted: Boolean): Boolean =
        isPremium && !restricted

    fun canAccessAdvancedMonitoring(isPremium: Boolean, restricted: Boolean): Boolean =
        isPremium && !restricted

    fun canLaunchPaywall(restricted: Boolean): Boolean = !restricted
}

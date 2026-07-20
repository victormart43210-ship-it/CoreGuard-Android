package com.coldboar.coreguard

/**
 * Entitlement tiers available in the app.
 *
 * Keep this enum in sync with the product IDs defined in the Play Console once
 * real billing is integrated.
 */
enum class EntitlementTier {
    /** Free tier – limited feature set. */
    FREE,

    /** Premium tier – full feature set. */
    PREMIUM
}

/**
 * Evaluates which features a user is entitled to based on the current
 * [BillingProvider] state.
 *
 * This class contains **policy logic only** – it does not perform purchases
 * or network calls. Inject a [BillingProvider] for production or test scenarios.
 *
 * When using [DemoBillingProvider], premium is a local demo flag only — not
 * purchase verification.
 *
 * @param billing The billing backend to query.
 */
class EntitlementPolicy(private val billing: BillingProvider) {

    /** Returns the user's current entitlement tier. */
    fun currentTier(): EntitlementTier =
        if (billing.isPremium()) EntitlementTier.PREMIUM else EntitlementTier.FREE

    /** Returns true when the user may access security-dashboard detail view. */
    fun canViewSecurityDashboard(): Boolean = true // available to all tiers

    /** Returns true when the user may export or share the security report. */
    fun canExportReport(): Boolean = currentTier() == EntitlementTier.PREMIUM

    /** Returns true when the user may access advanced threat monitoring. */
    fun canAccessAdvancedMonitoring(): Boolean = currentTier() == EntitlementTier.PREMIUM

    /**
     * Human-readable source label for UI honesty.
     * Distinguishes demo entitlement from a future Play-verified entitlement.
     */
    fun entitlementSourceLabel(): String =
        when (billing) {
            is DemoBillingProvider ->
                if (billing.isPremium()) "demo_premium" else "demo_free"
            is PlayBillingProvider ->
                if (billing.isPremium()) "play_verified_premium" else "play_free"
            else ->
                if (billing.isPremium()) "provider_premium" else "provider_free"
        }
}

/**
 * Convenience accessors that read from the process-wide [CoreGuardApp] when
 * available. Prefer injecting [EntitlementPolicy] in new code.
 *
 * Falls back to a local [DemoBillingProvider] only when the Application is not
 * yet initialized (unit tests). That fallback is **DEMO ONLY**.
 */
object Entitlements {

    private val fallbackDemo = DemoBillingProvider(startAsPremium = false)

    private fun policy(): EntitlementPolicy {
        return try {
            CoreGuardApp.get().entitlementPolicy
        } catch (_: IllegalStateException) {
            EntitlementPolicy(fallbackDemo)
        }
    }

    fun isPremium(): Boolean = policy().currentTier() == EntitlementTier.PREMIUM
    fun canViewSecurityDashboard(): Boolean = policy().canViewSecurityDashboard()
    fun canExportReport(): Boolean = policy().canExportReport()
    fun canAccessAdvancedMonitoring(): Boolean = policy().canAccessAdvancedMonitoring()
}

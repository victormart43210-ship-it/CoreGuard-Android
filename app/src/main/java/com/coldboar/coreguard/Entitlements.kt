package com.coldboar.coreguard

/**
 * Entitlement tiers available in the app.
 *
 * Keep this enum in sync with the product IDs defined in the Play Console.
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
 * Policy logic only – no purchases or network calls.
 *
 * Honesty:
 * - [BillingBackend.DEMO] premium = local demo flag only
 * - [BillingBackend.PLAY] premium = client-side Play cache only (not server-verified)
 */
class EntitlementPolicy(private val billing: BillingProvider) {

    fun currentTier(): EntitlementTier =
        if (billing.isPremium()) EntitlementTier.PREMIUM else EntitlementTier.FREE

    fun canViewSecurityDashboard(): Boolean = true

    fun canExportReport(): Boolean = currentTier() == EntitlementTier.PREMIUM

    fun canAccessAdvancedMonitoring(): Boolean = currentTier() == EntitlementTier.PREMIUM

    /**
     * Stable source label for tests/UI honesty.
     * Play premium is labeled `play_client_premium` — not "verified".
     */
    fun entitlementSourceLabel(): String =
        when (billing.backend) {
            BillingBackend.DEMO ->
                if (billing.isPremium()) "demo_premium" else "demo_free"
            BillingBackend.PLAY ->
                if (billing.isPremium()) "play_client_premium" else "play_free"
        }
}

/**
 * Convenience accessors via [CoreGuardApp] when available.
 * Falls back to demo billing only for unit tests without Application.
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

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
 * [BillingProvider] state and optional restricted mode.
 *
 * Honesty:
 * - DEMO premium = local demo flag only (not a purchase)
 * - PLAY premium = true only after billing-server verification in the Play path
 * - Restricted mode overrides premium for sensitive features
 */
class EntitlementPolicy(
    private val billing: BillingProvider,
    private val restrictedActive: Boolean = false
) {

    fun currentTier(): EntitlementTier =
        if (billing.isPremium()) EntitlementTier.PREMIUM else EntitlementTier.FREE

    fun canViewSecurityDashboard(): Boolean = true

    fun canExportReport(): Boolean =
        RestrictedMode.canExportReport(currentTier() == EntitlementTier.PREMIUM, restrictedActive)

    fun canAccessAdvancedMonitoring(): Boolean =
        RestrictedMode.canAccessAdvancedMonitoring(
            currentTier() == EntitlementTier.PREMIUM,
            restrictedActive
        )

    fun canLaunchPaywall(): Boolean = RestrictedMode.canLaunchPaywall(restrictedActive)

    fun entitlementSourceLabel(): String =
        when (billing.backend) {
            BillingBackend.DEMO ->
                if (billing.isPremium()) "demo_premium" else "demo_free"
            BillingBackend.PLAY ->
                if (billing.isPremium()) "play_verified_premium" else "play_free"
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

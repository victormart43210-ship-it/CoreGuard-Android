package com.coldboar.coreguard

/**
 * Entitlement tiers available in the app.
 *
 * Keep this enum in sync with the product IDs defined in the Play Console
 * ([PlayBillingProvider.PREMIUM_PRODUCT_ID]).
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
 * @param billing The billing backend to query.
 */
class EntitlementPolicy(private val billing: BillingProvider) {

    /** Returns the user's current entitlement tier. */
    fun currentTier(): EntitlementTier =
        if (billing.isPremium()) EntitlementTier.PREMIUM else EntitlementTier.FREE

    /** Returns true when the user may access security-dashboard detail view. */
    fun canViewSecurityDashboard(): Boolean = true  // available to all tiers

    /** Returns true when the user may export or share the security report. */
    fun canExportReport(): Boolean = currentTier() == EntitlementTier.PREMIUM

    /** Returns true when the user may access advanced threat monitoring. */
    fun canAccessAdvancedMonitoring(): Boolean = currentTier() == EntitlementTier.PREMIUM
}

/**
 * App-wide entitlement helpers backed by the production [PlayBillingProvider]
 * held on [CoreGuardApplication].
 *
 * Prefer injecting [EntitlementPolicy] in new code; this object exists for
 * call sites that cannot easily receive a provider.
 */
object Entitlements {

    private fun policy(): EntitlementPolicy {
        val billing = CoreGuardApplication.get()?.billingProvider
            ?: return EntitlementPolicy(DemoBillingProvider(startAsPremium = false))
        return EntitlementPolicy(billing)
    }

    fun isPremium(): Boolean = policy().currentTier() == EntitlementTier.PREMIUM
    fun canViewSecurityDashboard(): Boolean = policy().canViewSecurityDashboard()
    fun canExportReport(): Boolean = policy().canExportReport()
    fun canAccessAdvancedMonitoring(): Boolean = policy().canAccessAdvancedMonitoring()
}

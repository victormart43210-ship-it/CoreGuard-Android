package com.coldboar.coreguard

/**
 * Entitlement tiers available in the app.
 *
 * Keep this enum in sync with the product IDs defined in the Play Console
 * ([PlayBillingProvider.PREMIUM_PRODUCT_ID] / [EntitlementPolicy.PREMIUM_PRODUCT_ID]).
 */
enum class EntitlementTier {
    /** Free tier – core scan + shield + score. */
    FREE,

    /** Premium tier – exports, live signature refresh, deeper history, Quilla coaching. */
    PREMIUM
}

/**
 * Evaluates which features a user is entitled to based on the current
 * [BillingProvider] state.
 *
 * Policy logic only – does not perform purchases or network calls.
 */
class EntitlementPolicy(private val billing: BillingProvider) {

    fun currentTier(): EntitlementTier =
        if (billing.isPremium()) EntitlementTier.PREMIUM else EntitlementTier.FREE

    fun isPremium(): Boolean = currentTier() == EntitlementTier.PREMIUM

    /** Security dashboard / Guardian Score stays free for all. */
    fun canViewSecurityDashboard(): Boolean = true

    /** Compliance / report JSON export. */
    fun canExportReport(): Boolean = isPremium()

    fun canExportComplianceReport(): Boolean = canExportReport()

    /** Live IOC / threat signature refresh over the network. */
    fun canRefreshThreatSignatures(): Boolean = isPremium()

    /** Advanced monitoring / Quilla recommendations beyond basic Q&A. */
    fun canAccessAdvancedMonitoring(): Boolean = isPremium()

    fun canUseQuillaRecommendations(): Boolean = isPremium()

    /** Free users keep a short timeline; Premium keeps the full store. */
    fun maxTimelineEntries(): Int = if (isPremium()) PREMIUM_TIMELINE_ENTRIES else FREE_TIMELINE_ENTRIES

    companion object {
        const val FREE_TIMELINE_ENTRIES = 3
        const val PREMIUM_TIMELINE_ENTRIES = 25
        const val PREMIUM_PRODUCT_ID = "coreguard_premium_monthly"
    }
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

    fun isPremium(): Boolean = policy().isPremium()
    fun canViewSecurityDashboard(): Boolean = policy().canViewSecurityDashboard()
    fun canExportReport(): Boolean = policy().canExportReport()
    fun canAccessAdvancedMonitoring(): Boolean = policy().canAccessAdvancedMonitoring()
}

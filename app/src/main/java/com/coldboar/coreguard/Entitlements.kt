package com.coldboar.coreguard

/**
 * Entitlement tiers available in the app.
 *
 * Product ID source of truth: [BillingProvider.PREMIUM_PRODUCT_ID].
 */
enum class EntitlementTier {
    /** Free tier – core scan + shield + score + basic Quilla Q&A. */
    FREE,

    /** Premium tier – exports, live signature refresh, longer timeline, Premium coaching tips. */
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

    /**
     * Premium coaching tips (QuillaSalesCoach pitches / next-step premium guidance).
     * Basic Quilla Q&A and knowledge remain free for everyone.
     */
    fun canUseQuillaRecommendations(): Boolean = isPremium()

    /** Free users keep a short timeline; Premium keeps the full store. */
    fun maxTimelineEntries(): Int = if (isPremium()) PREMIUM_TIMELINE_ENTRIES else FREE_TIMELINE_ENTRIES

    companion object {
        const val FREE_TIMELINE_ENTRIES = 3
        const val PREMIUM_TIMELINE_ENTRIES = 25
        /** Alias of [BillingProvider.PREMIUM_PRODUCT_ID] for call-site convenience. */
        const val PREMIUM_PRODUCT_ID = BillingProvider.PREMIUM_PRODUCT_ID
    }
}

/**
 * App-wide entitlement helpers backed by the production [PlayBillingProvider]
 * held on [CoreGuardApplication].
 *
 * Prefer injecting [EntitlementPolicy] in new code; this object exists for
 * call sites that cannot easily receive a provider.
 *
 * When the Application is unavailable (rare process edge), fails closed as free —
 * never constructs [DemoBillingProvider] on a production path.
 */
object Entitlements {

    private fun policy(): EntitlementPolicy? {
        val billing = CoreGuardApplication.get()?.billingProvider ?: return null
        return EntitlementPolicy(billing)
    }

    fun isPremium(): Boolean = policy()?.isPremium() == true
    fun canViewSecurityDashboard(): Boolean = policy()?.canViewSecurityDashboard() ?: true
    fun canExportReport(): Boolean = policy()?.canExportReport() == true
    fun canUseQuillaRecommendations(): Boolean = policy()?.canUseQuillaRecommendations() == true
}

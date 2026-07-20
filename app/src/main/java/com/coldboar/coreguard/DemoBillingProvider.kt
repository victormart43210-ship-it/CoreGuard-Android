package com.coldboar.coreguard

import android.app.Activity

/**
 * **DEMO ONLY** – simulates a billing backend for local development and debug builds.
 *
 * Does not connect to Google Play Billing and does not verify purchase tokens.
 * Demo unlock is **not** purchase verification.
 *
 * Wired from [CoreGuardApp] when [BuildConfig.USE_DEMO_BILLING] is true.
 */
class DemoBillingProvider(
    private var startAsPremium: Boolean = false
) : BillingProvider {

    override val backend: BillingBackend = BillingBackend.DEMO

    private var premiumState: Boolean = startAsPremium

    override fun isPremium(): Boolean = premiumState

    override fun launchPurchaseFlow(
        activity: Activity?,
        productId: String,
        onResult: (PurchaseResult) -> Unit
    ) {
        // Demo: immediately "purchase" — no real payment. [activity] unused.
        premiumState = true
        onResult(PurchaseResult.Success)
    }

    /** Resets the simulated state back to the initial value (useful in tests). */
    fun reset() {
        premiumState = startAsPremium
    }
}

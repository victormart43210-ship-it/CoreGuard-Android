package com.coldboar.coreguard

/**
 * **DEMO ONLY** – simulates a billing backend for local development and testing.
 *
 * This implementation does not connect to Google Play Billing, does not verify
 * any real purchase token, and must not be shipped as a real billing gate.
 * Replace this with a real [BillingProvider] implementation before publishing.
 *
 * @param startAsPremium Initial simulated premium state. Defaults to false.
 */
class DemoBillingProvider(private var startAsPremium: Boolean = false) : BillingProvider {

    private var premiumState: Boolean = startAsPremium

    /**
     * Returns the current simulated premium state.
     * This is NOT backed by any real purchase or server verification.
     */
    override fun isPremium(): Boolean = premiumState

    /**
     * Simulates an instant successful purchase by flipping the internal flag.
     * No real payment is processed.
     */
    override fun launchPurchaseFlow(productId: String, onResult: (PurchaseResult) -> Unit) {
        // Demo: immediately "purchase" the product and report success.
        premiumState = true
        onResult(PurchaseResult.Success)
    }

    /** Resets the simulated state back to the initial value (useful in tests). */
    fun reset() {
        premiumState = startAsPremium
    }
}

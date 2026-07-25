package com.coldboar.coreguard

/**
 * Test/preview [BillingProvider] that simulates purchases instantly.
 *
 * Production uses [PlayBillingProvider]. Keep this class for JVM unit tests and
 * Compose previews only — never attach it as the shipped billing path.
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

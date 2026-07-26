package com.coldboar.coreguard

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Test/preview [BillingProvider] that simulates purchases instantly.
 *
 * Production uses [PlayBillingProvider]. Keep this class for JVM unit tests and
 * Compose previews only — never attach it as the shipped billing path.
 *
 * @param startAsPremium Initial simulated premium state. Defaults to false.
 */
class DemoBillingProvider(private var startAsPremium: Boolean = false) : BillingProvider {

    private val premium = MutableStateFlow(startAsPremium)

    override val premiumState: StateFlow<Boolean> = premium.asStateFlow()

    override fun isPremium(): Boolean = premium.value

    override fun launchPurchaseFlow(productId: String, onResult: (PurchaseResult) -> Unit) {
        if (productId != BillingProvider.PREMIUM_PRODUCT_ID) {
            onResult(PurchaseResult.Error("Unknown product ID"))
            return
        }
        premium.value = true
        onResult(PurchaseResult.Success)
    }

    /** Resets the simulated state back to the initial value (useful in tests). */
    fun reset() {
        premium.value = startAsPremium
    }
}

package com.coldboar.coreguard

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fail-closed billing stub used when Play Billing cannot be resolved.
 *
 * Always reports non-premium. Never unlocks features. Production Compose must
 * use this instead of [DemoBillingProvider] when [BillingModule] is unavailable.
 */
class FailClosedBillingProvider : BillingProvider {

    private val _premium = MutableStateFlow(false)
    override val premiumState: StateFlow<Boolean> = _premium.asStateFlow()

    override fun isPremium(): Boolean = false

    override fun premiumPriceLabel(): String = ""

    override fun launchPurchaseFlow(productId: String, onResult: (PurchaseResult) -> Unit) {
        if (productId != BillingProvider.PREMIUM_PRODUCT_ID) {
            onResult(PurchaseResult.Error("Unknown product"))
            return
        }
        onResult(PurchaseResult.Error("Play Billing unavailable"))
    }
}

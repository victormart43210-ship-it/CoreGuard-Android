package com.coldboar.coreguard

/**
 * Placeholder for a future Google Play Billing Library integration.
 *
 * This class intentionally does **not** grant premium access and does **not**
 * simulate purchases. Wire [BillingClient] here when ready for production:
 *
 * 1. Add `com.android.billingclient:billing-ktx` dependency.
 * 2. Connect [BillingClient], query purchases, and launch billing flows.
 * 3. Verify purchase tokens **server-side** before granting entitlement.
 * 4. Swap [DemoBillingProvider] for this class in [CoreGuardApp].
 *
 * Until then, constructing this provider and calling its methods will fail
 * loudly so demo unlock cannot be mistaken for real verification.
 */
class PlayBillingProvider : BillingProvider {

    override fun isPremium(): Boolean {
        // Real Play Billing is not integrated. Never return true from here.
        return false
    }

    override fun launchPurchaseFlow(productId: String, onResult: (PurchaseResult) -> Unit) {
        onResult(
            PurchaseResult.Error(
                "Google Play Billing is not integrated yet. " +
                    "Use DemoBillingProvider only for local demos — it is not purchase verification."
            )
        )
    }
}

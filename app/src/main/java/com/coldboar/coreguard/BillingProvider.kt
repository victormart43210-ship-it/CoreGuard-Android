package com.coldboar.coreguard

/**
 * Contract for the app's billing/subscription backend.
 *
 * Implement this interface with a real Google Play Billing implementation when
 * ready for production. The [DemoBillingProvider] is the only implementation
 * that ships in this repository; it must NOT be used as evidence of real billing.
 */
interface BillingProvider {

    /**
     * Returns true when the user currently holds an active premium entitlement.
     *
     * This must be a fast, local/cached check suitable for calling on the main
     * thread (e.g., return a cached value from a previous network call).
     */
    fun isPremium(): Boolean

    /**
     * Initiates the purchase flow for the given [productId].
     *
     * The [onResult] callback is invoked with the result:
     * - [PurchaseResult.Success] on confirmed purchase.
     * - [PurchaseResult.Cancelled] when the user dismissed the flow.
     * - [PurchaseResult.Error] on billing service failure.
     */
    fun launchPurchaseFlow(productId: String, onResult: (PurchaseResult) -> Unit)
}

/** Result of a [BillingProvider.launchPurchaseFlow] call. */
sealed class PurchaseResult {
    /** The purchase was acknowledged successfully. */
    object Success : PurchaseResult()

    /** The user cancelled the purchase flow. */
    object Cancelled : PurchaseResult()

    /** A billing error occurred. [message] contains a non-sensitive description. */
    data class Error(val message: String) : PurchaseResult()
}

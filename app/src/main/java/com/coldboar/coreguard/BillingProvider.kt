package com.coldboar.coreguard

import android.app.Activity

/**
 * Identifies which billing backend is active — used for honest UI labeling.
 *
 * [PLAY] means Google Play Billing Library is used on-device. It does **not**
 * mean purchase tokens have been verified server-side.
 */
enum class BillingBackend {
    /** Local simulated unlock. Not a purchase. */
    DEMO,

    /** Google Play Billing Library (client-side). Server verification still required for production trust. */
    PLAY
}

/**
 * Contract for the app's billing/subscription backend.
 *
 * Implementations:
 * - [DemoBillingProvider] — local demo only; never treat as purchase verification.
 * - [PlayBillingProvider] — Google Play Billing Library; client cache only until
 *   server-side token verification is added.
 */
interface BillingProvider {

    /** Which backend this instance represents (for UI honesty). */
    val backend: BillingBackend

    /**
     * Returns true when the user currently holds an active premium entitlement
     * according to this provider's local/cached state.
     *
     * This must be a fast check suitable for the main thread.
     * For [BillingBackend.PLAY], a true result is **client-side only** until
     * server verification exists.
     */
    fun isPremium(): Boolean

    /**
     * Initiates the purchase (or demo unlock) flow for [productId].
     *
     * [activity] is required for Google Play Billing's purchase sheet.
     * [DemoBillingProvider] ignores it (may be null in unit tests).
     */
    fun launchPurchaseFlow(
        activity: Activity?,
        productId: String,
        onResult: (PurchaseResult) -> Unit
    )

    /**
     * Optional refresh of cached purchase state (e.g. query Play purchases).
     * Default no-op for providers that do not need it.
     */
    fun refreshPurchases(onComplete: (() -> Unit)? = null) {
        onComplete?.invoke()
    }

    /** Release billing connections when the process no longer needs them. */
    fun destroy() = Unit
}

/** Result of a [BillingProvider.launchPurchaseFlow] call. */
sealed class PurchaseResult {
    /** Purchase acknowledged (Play) or demo unlock applied. Not server-verified by itself. */
    object Success : PurchaseResult()

    /** The user cancelled the purchase flow. */
    object Cancelled : PurchaseResult()

    /** A billing error occurred. [message] contains a non-sensitive description. */
    data class Error(val message: String) : PurchaseResult()
}

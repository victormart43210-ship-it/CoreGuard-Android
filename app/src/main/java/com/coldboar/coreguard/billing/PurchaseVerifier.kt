package com.coldboar.coreguard.billing

/**
 * Request payload for server-side Play purchase verification.
 * Never log [purchaseToken] in production analytics.
 */
data class PurchaseVerifyRequest(
    val packageName: String,
    val productId: String,
    val purchaseToken: String
)

/** Outcome of [PurchaseVerifier.verify]. */
sealed class VerificationResult {
    /** Google Play (via backend) confirmed an active entitlement. */
    data class Verified(val expiryTimeMillis: Long? = null) : VerificationResult()

    /** Backend reached; purchase is not active / not valid. */
    data class Denied(val reason: String) : VerificationResult()

    /** Network / config / backend failure — do not treat as entitled. */
    data class Error(val message: String) : VerificationResult()
}

/**
 * Server-side purchase verification boundary.
 *
 * Production must call a backend that uses the Google Play Developer API.
 * Client-side BillingClient state alone is not sufficient.
 */
interface PurchaseVerifier {
    fun verify(request: PurchaseVerifyRequest, onResult: (VerificationResult) -> Unit)
}

/**
 * Used when [com.coldboar.coreguard.BuildConfig.VERIFICATION_BASE_URL] is empty.
 * Never grants entitlement — forces an explicit backend configuration.
 */
class UnconfiguredPurchaseVerifier : PurchaseVerifier {
    override fun verify(request: PurchaseVerifyRequest, onResult: (VerificationResult) -> Unit) {
        onResult(
            VerificationResult.Error(
                "Purchase verification URL is not configured. " +
                    "Set COREGUARD_VERIFY_URL when building release, then deploy billing-server."
            )
        )
    }
}

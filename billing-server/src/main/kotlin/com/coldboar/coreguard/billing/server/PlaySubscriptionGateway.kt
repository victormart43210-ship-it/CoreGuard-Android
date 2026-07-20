package com.coldboar.coreguard.billing.server

/**
 * Result of verifying a Play subscription purchase token.
 */
data class SubscriptionVerifyOutcome(
    val active: Boolean,
    val reason: String? = null,
    val expiryTimeMillis: Long? = null
)

/**
 * Abstraction over Google Play Developer API so unit tests can use a mock.
 */
interface PlaySubscriptionGateway {
    fun verifySubscription(
        packageName: String,
        productId: String,
        purchaseToken: String
    ): SubscriptionVerifyOutcome
}

/**
 * Accepts well-known mock tokens for local/CI testing without Play credentials.
 * Token format: `mock_active_<productId>` or `mock_expired_<productId>`.
 */
class MockPlaySubscriptionGateway : PlaySubscriptionGateway {
    override fun verifySubscription(
        packageName: String,
        productId: String,
        purchaseToken: String
    ): SubscriptionVerifyOutcome {
        return when {
            purchaseToken == "mock_active_$productId" ->
                SubscriptionVerifyOutcome(
                    active = true,
                    expiryTimeMillis = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000
                )
            purchaseToken.startsWith("mock_expired_") ->
                SubscriptionVerifyOutcome(active = false, reason = "Mock token marked expired.")
            else ->
                SubscriptionVerifyOutcome(
                    active = false,
                    reason = "Unknown mock token. Use mock_active_$productId in COREGUARD_VERIFY_MODE=mock."
                )
        }
    }
}

package com.coldboar.coreguard.security

import com.coldboar.coreguard.BillingBackend
import com.coldboar.coreguard.BillingProvider
import com.coldboar.coreguard.EntitlementPolicy
import com.coldboar.coreguard.EntitlementTier
import com.coldboar.coreguard.PaywallActivity

/**
 * Builds and persists entitlement snapshots without treating the cache as a grant.
 *
 * Live [BillingProvider.isPremium] remains the source of truth for feature gates.
 * Cached rows are for cold-start UI honesty labels and diagnostics only.
 */
object EntitlementCacheWriter {

    fun captureLive(
        billing: BillingProvider,
        policy: EntitlementPolicy,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): CachedEntitlementSnapshot {
        val premium = billing.isPremium()
        return CachedEntitlementSnapshot(
            tier = if (premium) EntitlementTier.PREMIUM else EntitlementTier.FREE,
            backend = billing.backend,
            sourceLabel = policy.entitlementSourceLabel(),
            productId = if (premium) PaywallActivity.PRODUCT_ID_PREMIUM else null,
            purchaseTokenPresent = premium && billing.backend == BillingBackend.PLAY,
            verifiedAtEpochMs = if (premium && billing.backend == BillingBackend.PLAY) nowEpochMs else null,
            serverMessage = when {
                billing.backend == BillingBackend.DEMO && premium ->
                    "demo unlock only; not a purchase"
                billing.backend == BillingBackend.PLAY && premium ->
                    "server-verified (cached label; refresh still required)"
                billing.backend == BillingBackend.PLAY ->
                    "awaiting Play purchase + server verification"
                else -> "demo free"
            },
        )
    }

    fun persistLive(
        store: SecureStore,
        billing: BillingProvider,
        policy: EntitlementPolicy,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): CachedEntitlementSnapshot {
        val snapshot = captureLive(billing, policy, nowEpochMs)
        store.writeEntitlementSnapshot(snapshot)
        return snapshot
    }
}

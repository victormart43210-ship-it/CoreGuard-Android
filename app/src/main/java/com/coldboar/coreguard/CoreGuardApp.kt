package com.coldboar.coreguard

import android.app.Application
import com.coldboar.coreguard.billing.HttpPurchaseVerifier
import com.coldboar.coreguard.billing.PurchaseVerifier
import com.coldboar.coreguard.billing.UnconfiguredPurchaseVerifier
import com.coldboar.coreguard.security.CachedEntitlementSnapshot
import com.coldboar.coreguard.security.EntitlementCacheWriter
import com.coldboar.coreguard.security.SecureStore

/**
 * Application entry point.
 *
 * Billing selection:
 * - Debug ([BuildConfig.USE_DEMO_BILLING] = true) → [DemoBillingProvider]
 * - Release → [PlayBillingProvider] gated by [PurchaseVerifier] (billing-server)
 *
 * Demo unlock is never presented as purchase verification.
 * [SecureStore] caches entitlement labels only — never grants premium by itself.
 */
class CoreGuardApp : Application() {

    val purchaseVerifier: PurchaseVerifier by lazy {
        val baseUrl = BuildConfig.VERIFICATION_BASE_URL.trim()
        if (baseUrl.isEmpty()) {
            UnconfiguredPurchaseVerifier()
        } else {
            HttpPurchaseVerifier(baseUrl)
        }
    }

    val billingProvider: BillingProvider by lazy {
        if (BuildConfig.USE_DEMO_BILLING) {
            DemoBillingProvider(startAsPremium = false)
        } else {
            PlayBillingProvider(this, purchaseVerifier)
        }
    }

    val subscriptionManager: SubscriptionManager by lazy { SubscriptionManager(billingProvider) }

    val entitlementPolicy: EntitlementPolicy by lazy { EntitlementPolicy(billingProvider) }

    val secureStore: SecureStore by lazy { SecureStore.create(this) }

    /** Last cached entitlement snapshot (stale hint only; not a premium grant). */
    @Volatile
    var lastCachedEntitlement: CachedEntitlementSnapshot? = null
        private set

    companion object {
        @Volatile
        private var instance: CoreGuardApp? = null

        fun get(): CoreGuardApp =
            instance ?: error("CoreGuardApp not initialized")
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        lastCachedEntitlement = runCatching { secureStore.readEntitlementSnapshot() }.getOrNull()
        if (!BuildConfig.USE_DEMO_BILLING) {
            billingProvider.refreshPurchases { persistEntitlementCache() }
        } else {
            persistEntitlementCache()
        }
    }

    /** Writes a live billing snapshot into encrypted storage. Does not grant premium. */
    fun persistEntitlementCache() {
        lastCachedEntitlement = EntitlementCacheWriter.persistLive(
            store = secureStore,
            billing = billingProvider,
            policy = entitlementPolicy,
        )
    }

    override fun onTerminate() {
        billingProvider.destroy()
        super.onTerminate()
    }
}

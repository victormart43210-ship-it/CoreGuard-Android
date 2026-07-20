package com.coldboar.coreguard

import android.app.Application
import com.coldboar.coreguard.billing.HttpPurchaseVerifier
import com.coldboar.coreguard.billing.PurchaseVerifier
import com.coldboar.coreguard.billing.UnconfiguredPurchaseVerifier

/**
 * Application entry point.
 *
 * Billing selection:
 * - Debug ([BuildConfig.USE_DEMO_BILLING] = true) → [DemoBillingProvider]
 * - Release → [PlayBillingProvider] gated by [PurchaseVerifier] (billing-server)
 *
 * Demo unlock is never presented as purchase verification.
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

    companion object {
        @Volatile
        private var instance: CoreGuardApp? = null

        fun get(): CoreGuardApp =
            instance ?: error("CoreGuardApp not initialized")
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        if (!BuildConfig.USE_DEMO_BILLING) {
            billingProvider.refreshPurchases(null)
        }
    }

    override fun onTerminate() {
        billingProvider.destroy()
        super.onTerminate()
    }
}

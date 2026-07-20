package com.coldboar.coreguard

import android.app.Application

/**
 * Application entry point.
 *
 * Owns the shared [BillingProvider] and [SubscriptionManager] so Activities
 * do not create divergent billing state.
 *
 * Billing selection:
 * - Debug ([BuildConfig.USE_DEMO_BILLING] = true) → [DemoBillingProvider]
 * - Release → [PlayBillingProvider] (client-side Play Billing; not server-verified)
 *
 * Demo unlock is never presented as purchase verification.
 */
class CoreGuardApp : Application() {

    /**
     * Shared billing backend for this process.
     * Debug uses demo; release uses Google Play Billing Library.
     */
    val billingProvider: BillingProvider by lazy {
        if (BuildConfig.USE_DEMO_BILLING) {
            DemoBillingProvider(startAsPremium = false)
        } else {
            PlayBillingProvider(this)
        }
    }

    /** Shared subscription / paywall launch guard for this process. */
    val subscriptionManager: SubscriptionManager by lazy { SubscriptionManager(billingProvider) }

    /** Policy evaluator bound to the shared billing provider. */
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
        // Warm Play connection / refresh cached purchases when using Play backend.
        if (!BuildConfig.USE_DEMO_BILLING) {
            billingProvider.refreshPurchases(null)
        }
    }

    override fun onTerminate() {
        billingProvider.destroy()
        super.onTerminate()
    }
}

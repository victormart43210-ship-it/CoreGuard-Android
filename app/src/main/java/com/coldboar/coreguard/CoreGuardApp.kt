package com.coldboar.coreguard

import android.app.Application

/**
 * Application entry point.
 *
 * Owns the shared [BillingProvider] and [SubscriptionManager] so Activities
 * do not create divergent demo billing state.
 *
 * **DEMO ONLY**: The default provider is [DemoBillingProvider]. Replace with a
 * real Play Billing implementation before any production release that charges
 * users. Demo unlock is not purchase verification.
 */
class CoreGuardApp : Application() {

    /**
     * Shared billing backend for this process.
     * Demo path only — not Google Play Billing.
     */
    val billingProvider: BillingProvider by lazy { DemoBillingProvider(startAsPremium = false) }

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
    }
}

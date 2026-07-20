package com.coldboar.coreguard

import android.content.Context
import android.content.Intent

/**
 * Manages subscription state and guards against duplicate paywall launches.
 *
 * @param billing The [BillingProvider] to query for premium status.
 */
class SubscriptionManager(private val billing: BillingProvider) {

    /** True while the paywall is already on the screen. */
    @Volatile private var paywallVisible: Boolean = false

    /** Returns true when the user currently holds a premium entitlement. */
    fun isPremium(): Boolean = billing.isPremium()

    /**
     * Launches [PaywallActivity] only if it is not already visible.
     *
     * This guard prevents duplicate launches when rapid user interaction or
     * lifecycle events trigger the paywall multiple times in quick succession.
     *
     * @return true if the activity was launched, false if it was already visible.
     */
    fun launchPaywallIfNotShowing(context: Context): Boolean {
        if (paywallVisible) return false
        paywallVisible = true
        val intent = Intent(context, PaywallActivity::class.java)
        context.startActivity(intent)
        return true
    }

    /**
     * Must be called when [PaywallActivity] finishes (e.g., in
     * [PaywallActivity.onDestroy]) so the guard is reset.
     */
    fun onPaywallDismissed() {
        paywallVisible = false
    }
}

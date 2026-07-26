package com.coldboar.coreguard

import android.content.Context
import android.content.Intent

/**
 * Manages subscription state and guards against duplicate paywall launches.
 *
 * @param billing The [BillingProvider] to query for premium status.
 */
class SubscriptionManager(private val billing: BillingProvider) {

    /** Returns true when the user currently holds a premium entitlement. */
    fun isPremium(): Boolean = billing.isPremium()

    /**
     * Launches [PaywallActivity] only if it is not already visible.
     *
     * @return true if the activity was launched, false if it was already visible.
     */
    fun launchPaywallIfNotShowing(context: Context): Boolean {
        if (paywallVisible) return false
        paywallVisible = true
        val intent = Intent(context, PaywallActivity::class.java)
        if (context !is android.app.Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return true
    }

    /**
     * Must be called when [PaywallActivity] finishes so the guard is reset.
     */
    fun onPaywallDismissed() {
        paywallVisible = false
    }

    companion object {
        @Volatile
        private var paywallVisible: Boolean = false
    }
}

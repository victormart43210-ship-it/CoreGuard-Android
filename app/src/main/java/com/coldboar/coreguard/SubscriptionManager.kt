package com.coldboar.coreguard

import android.content.Context
import android.content.Intent

/**
 * Manages subscription state and guards against duplicate paywall launches.
 *
 * @param billing The [BillingProvider] to query for premium status.
 */
class SubscriptionManager(
    private val billing: BillingProvider
) {

    /** True while the paywall is already on the screen (or launch is in flight). */
    @Volatile
    private var paywallVisible: Boolean = false

    /** Returns true when the user currently holds a premium entitlement. */
    fun isPremium(): Boolean = billing.isPremium()

    /** Exposed for tests — whether the duplicate-launch guard is active. */
    fun isPaywallVisible(): Boolean = paywallVisible

    /**
     * Executes [launch] only if a paywall is not already visible.
     *
     * Prefer this overload in unit tests. Production code typically uses
     * [launchPaywallIfNotShowing] with a [Context].
     *
     * @return true if [launch] ran, false if the guard blocked it.
     */
    fun launchPaywallIfNotShowing(launch: () -> Unit): Boolean {
        if (paywallVisible) return false
        paywallVisible = true
        launch()
        return true
    }

    /**
     * Launches [PaywallActivity] only if it is not already visible.
     *
     * This guard prevents duplicate launches when rapid user interaction or
     * lifecycle events trigger the paywall multiple times in quick succession.
     *
     * @return true if the activity was launched, false if it was already visible.
     */
    fun launchPaywallIfNotShowing(context: Context): Boolean {
        return launchPaywallIfNotShowing {
            context.startActivity(Intent(context, PaywallActivity::class.java))
        }
    }

    /**
     * Must be called when [PaywallActivity] finishes (e.g. in
     * [MainActivity.onResume] after returning) so the guard is reset.
     */
    fun onPaywallDismissed() {
        paywallVisible = false
    }
}

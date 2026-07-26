package com.coldboar.coreguard

/**
 * Public module façade for Play Billing / premium entitlement access.
 *
 * Screens should prefer this (or an injected [BillingProvider]) over reaching into
 * [CoreGuardApplication] fields directly.
 */
object BillingModule {

    const val PREMIUM_PRODUCT_ID: String = BillingProvider.PREMIUM_PRODUCT_ID

    fun provider(): BillingProvider = CoreGuardApplication.require().billingProvider

    fun isPremium(): Boolean = provider().isPremium()

    fun policy(): EntitlementPolicy = EntitlementPolicy(provider())
}

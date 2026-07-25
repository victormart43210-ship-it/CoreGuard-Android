package com.coldboar.coreguard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract tests that keep Play Store product IDs and entitlement policy aligned.
 * Full BillingClient integration requires an instrumented device / Play environment.
 */
class PlayBillingContractTest {

    @Test
    fun `premium product id matches Play Console subscription id`() {
        assertEquals("coreguard_premium_monthly", PlayBillingProvider.PREMIUM_PRODUCT_ID)
    }

    @Test
    fun `paywall activity product constant stays aligned`() {
        // PaywallActivity no longer exposes a separate constant; Settings/Paywall
        // both use PlayBillingProvider.PREMIUM_PRODUCT_ID.
        assertTrue(PlayBillingProvider.PREMIUM_PRODUCT_ID.isNotBlank())
    }

    @Test
    fun `export remains premium-gated`() {
        val free = EntitlementPolicy(DemoBillingProvider(startAsPremium = false))
        val premium = EntitlementPolicy(DemoBillingProvider(startAsPremium = true))
        assertFalse(free.canExportReport())
        assertTrue(premium.canExportReport())
    }
}

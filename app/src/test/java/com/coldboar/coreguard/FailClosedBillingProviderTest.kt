package com.coldboar.coreguard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FailClosedBillingProviderTest {

    @Test
    fun `never reports premium`() {
        val p = FailClosedBillingProvider()
        assertFalse(p.isPremium())
        assertFalse(p.premiumState.value)
    }

    @Test
    fun `unknown product does not unlock`() {
        val p = FailClosedBillingProvider()
        var result: PurchaseResult? = null
        p.launchPurchaseFlow("totally.wrong.sku") { result = it }
        assertTrue(result is PurchaseResult.Error)
        assertFalse(p.isPremium())
    }

    @Test
    fun `authoritative product still fails closed without Play`() {
        val p = FailClosedBillingProvider()
        var result: PurchaseResult? = null
        p.launchPurchaseFlow(BillingProvider.PREMIUM_PRODUCT_ID) { result = it }
        assertTrue(result is PurchaseResult.Error)
        assertEquals(false, p.isPremium())
    }
}

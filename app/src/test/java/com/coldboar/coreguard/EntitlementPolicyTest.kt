package com.coldboar.coreguard

import android.app.Activity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for entitlement / billing boundary (JVM, no Play services).
 */
class EntitlementPolicyTest {

    @Test
    fun `DemoBillingProvider starts as non-premium by default`() {
        assertFalse(DemoBillingProvider().isPremium())
    }

    @Test
    fun `DemoBillingProvider can start as premium`() {
        assertTrue(DemoBillingProvider(startAsPremium = true).isPremium())
    }

    @Test
    fun `DemoBillingProvider launchPurchaseFlow returns Success and sets premium`() {
        val provider = DemoBillingProvider()
        var result: PurchaseResult? = null
        provider.launchPurchaseFlow(activity = null, productId = "test_product") { result = it }

        assertTrue(result is PurchaseResult.Success)
        assertTrue(provider.isPremium())
        assertEquals(BillingBackend.DEMO, provider.backend)
    }

    @Test
    fun `DemoBillingProvider reset returns to initial state`() {
        val provider = DemoBillingProvider(startAsPremium = false)
        provider.launchPurchaseFlow(null, "test_product") {}
        assertTrue(provider.isPremium())
        provider.reset()
        assertFalse(provider.isPremium())
    }

    @Test
    fun `EntitlementPolicy FREE tier gates`() {
        val policy = EntitlementPolicy(DemoBillingProvider(startAsPremium = false))
        assertEquals(EntitlementTier.FREE, policy.currentTier())
        assertTrue(policy.canViewSecurityDashboard())
        assertFalse(policy.canExportReport())
        assertFalse(policy.canAccessAdvancedMonitoring())
        assertEquals("demo_free", policy.entitlementSourceLabel())
    }

    @Test
    fun `EntitlementPolicy PREMIUM demo tier gates`() {
        val policy = EntitlementPolicy(DemoBillingProvider(startAsPremium = true))
        assertEquals(EntitlementTier.PREMIUM, policy.currentTier())
        assertTrue(policy.canExportReport())
        assertTrue(policy.canAccessAdvancedMonitoring())
        assertEquals("demo_premium", policy.entitlementSourceLabel())
    }

    @Test
    fun `EntitlementPolicy labels Play backend without claiming server verification`() {
        val playStub = object : BillingProvider {
            override val backend = BillingBackend.PLAY
            override fun isPremium() = true
            override fun launchPurchaseFlow(
                activity: Activity?,
                productId: String,
                onResult: (PurchaseResult) -> Unit
            ) {
                onResult(PurchaseResult.Error("unused in test"))
            }
        }
        assertEquals("play_verified_premium", EntitlementPolicy(playStub).entitlementSourceLabel())
    }

    @Test
    fun `EntitlementPolicy labels Play free honestly`() {
        val playStub = object : BillingProvider {
            override val backend = BillingBackend.PLAY
            override fun isPremium() = false
            override fun launchPurchaseFlow(
                activity: Activity?,
                productId: String,
                onResult: (PurchaseResult) -> Unit
            ) {
                onResult(PurchaseResult.Cancelled)
            }
        }
        assertEquals("play_free", EntitlementPolicy(playStub).entitlementSourceLabel())
    }

    @Test
    fun `SubscriptionManager isPremium mirrors billing provider`() {
        val billing = DemoBillingProvider(startAsPremium = false)
        val sm = SubscriptionManager(billing)
        assertFalse(sm.isPremium())
        billing.launchPurchaseFlow(null, "test") {}
        assertTrue(sm.isPremium())
    }

    @Test
    fun `SubscriptionManager prevents duplicate paywall launches`() {
        var launchCount = 0
        val sm = SubscriptionManager(DemoBillingProvider())

        assertTrue(sm.launchPaywallIfNotShowing { launchCount++ })
        assertFalse(sm.launchPaywallIfNotShowing { launchCount++ })
        assertEquals(1, launchCount)
        assertTrue(sm.isPaywallVisible())

        sm.onPaywallDismissed()
        assertFalse(sm.isPaywallVisible())
        assertTrue(sm.launchPaywallIfNotShowing { launchCount++ })
        assertEquals(2, launchCount)
    }
}

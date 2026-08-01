package com.coldboar.coreguard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Matrix of premium gates for free / premium / fail-closed billing paths.
 * Production UI must never unlock Premium features via demo defaults.
 */
class PremiumGatingMatrixTest {

    private data class Expectation(
        val canExport: Boolean,
        val canRefreshSignatures: Boolean,
        val canUseQuillaTips: Boolean,
        val timelineEntries: Int,
        val dashboard: Boolean = true
    )

    @Test
    fun `free demo billing keeps premium features locked`() {
        assertMatrix(
            EntitlementPolicy(DemoBillingProvider(startAsPremium = false)),
            Expectation(
                canExport = false,
                canRefreshSignatures = false,
                canUseQuillaTips = false,
                timelineEntries = EntitlementPolicy.FREE_TIMELINE_ENTRIES
            )
        )
    }

    @Test
    fun `premium demo billing unlocks gated features only`() {
        assertMatrix(
            EntitlementPolicy(DemoBillingProvider(startAsPremium = true)),
            Expectation(
                canExport = true,
                canRefreshSignatures = true,
                canUseQuillaTips = true,
                timelineEntries = EntitlementPolicy.PREMIUM_TIMELINE_ENTRIES
            )
        )
    }

    @Test
    fun `fail-closed billing matches free gates`() {
        assertMatrix(
            EntitlementPolicy(FailClosedBillingProvider()),
            Expectation(
                canExport = false,
                canRefreshSignatures = false,
                canUseQuillaTips = false,
                timelineEntries = EntitlementPolicy.FREE_TIMELINE_ENTRIES
            )
        )
    }

    @Test
    fun `fail-closed purchase of authoritative SKU stays free`() {
        val billing = FailClosedBillingProvider()
        var result: PurchaseResult? = null
        billing.launchPurchaseFlow(BillingProvider.PREMIUM_PRODUCT_ID) { result = it }
        assertTrue(result is PurchaseResult.Error)
        assertFalse(EntitlementPolicy(billing).isPremium())
        assertFalse(EntitlementPolicy(billing).canExportReport())
    }

    @Test
    fun `demo purchase of wrong SKU does not unlock premium`() {
        val billing = DemoBillingProvider(startAsPremium = false)
        var result: PurchaseResult? = null
        billing.launchPurchaseFlow("wrong.sku") { result = it }
        assertTrue(result is PurchaseResult.Error)
        assertFalse(billing.isPremium())
        assertFalse(EntitlementPolicy(billing).canRefreshThreatSignatures())
    }

    @Test
    fun `demo purchase of authoritative SKU unlocks premium gates`() {
        val billing = DemoBillingProvider(startAsPremium = false)
        var result: PurchaseResult? = null
        billing.launchPurchaseFlow(BillingProvider.PREMIUM_PRODUCT_ID) { result = it }
        assertTrue(result is PurchaseResult.Success)
        assertMatrix(
            EntitlementPolicy(billing),
            Expectation(
                canExport = true,
                canRefreshSignatures = true,
                canUseQuillaTips = true,
                timelineEntries = EntitlementPolicy.PREMIUM_TIMELINE_ENTRIES
            )
        )
    }

    private fun assertMatrix(policy: EntitlementPolicy, expected: Expectation) {
        assertEquals(expected.canExport, policy.canExportReport())
        assertEquals(expected.canExport, policy.canExportComplianceReport())
        assertEquals(expected.canRefreshSignatures, policy.canRefreshThreatSignatures())
        assertEquals(expected.canUseQuillaTips, policy.canUseQuillaRecommendations())
        assertEquals(expected.timelineEntries, policy.maxTimelineEntries())
        assertEquals(expected.dashboard, policy.canViewSecurityDashboard())
    }
}

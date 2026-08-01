package com.coldboar.coreguard.elite

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

class ScamGuardEngineTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        ScamGuardEngine.clear()
        ForensicJournal.memoryStore = mutableListOf()
        context = mock(Context::class.java)
    }

    @Test
    fun `extractUrls finds http and https links`() {
        val text = "Check https://evil.example/login and http://10.0.0.1/otp now"
        val urls = ScamGuardEngine.extractUrls(text)
        assertEquals(2, urls.size)
        assertTrue(urls.any { it.contains("evil.example") })
        assertTrue(urls.any { it.contains("10.0.0.1") })
    }

    @Test
    fun `scoreUrl flags raw IP and bait keywords`() {
        val finding = ScamGuardEngine.scoreUrl(
            context,
            "https://203.0.113.9/secure-login/wallet-connect",
            source = "test"
        )
        assertNotNull(finding)
        assertTrue(finding!!.score >= 50)
        assertTrue(finding.reasons.any { it.contains("IP", ignoreCase = true) })
    }

    @Test
    fun `scoreUrl flags spoof bank host`() {
        val finding = ScamGuardEngine.scoreUrl(
            context,
            "https://paypa1-secure.top/login",
            source = "test"
        )
        assertNotNull(finding)
        assertTrue(finding!!.score >= 50)
        assertTrue(finding.reasons.any { it.contains("spoof", ignoreCase = true) })
    }

    @Test
    fun `benign looking host without heuristics returns null or low watch`() {
        val finding = ScamGuardEngine.scoreUrl(
            context,
            "https://developer.android.com/guide",
            source = "test"
        )
        // No IOC on mock Context; no bait/TLD/IP → null
        assertNull(finding)
    }

    @Test
    fun `inspectNotificationText publishes amber finding`() {
        val finding = ScamGuardEngine.inspectNotificationText(
            context,
            "Urgent: verify-account at https://chase-secure.xyz/otp",
            source = "com.android.messaging"
        )
        assertNotNull(finding)
        assertEquals(finding, ScamGuardEngine.latestFinding())
        assertTrue(ScamGuardEngine.recentFindings().isNotEmpty())
    }
}

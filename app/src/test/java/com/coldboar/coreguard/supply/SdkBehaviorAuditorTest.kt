package com.coldboar.coreguard.supply

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SdkBehaviorAuditor].
 */
class SdkBehaviorAuditorTest {

    @Before
    fun reset() = SdkBehaviorAuditor.reset()

    @Test
    fun `clean URL is not flagged as sensitive`() {
        SdkBehaviorAuditor.record("com.example.sdk", "GET", "https://api.example.com/items")
        val summaries = SdkBehaviorAuditor.summaries()
        assertEquals(1, summaries.size)
        assertFalse(summaries[0].flagged)
        assertEquals(0, summaries[0].sensitiveRequests)
    }

    @Test
    fun `URL containing device_id is flagged`() {
        SdkBehaviorAuditor.record("com.analytics", "POST", "https://collect.example.com?device_id=abc123")
        val summaries = SdkBehaviorAuditor.summaries()
        assertTrue(summaries[0].flagged)
        assertEquals(1, summaries[0].sensitiveRequests)
    }

    @Test
    fun `URL containing location is flagged`() {
        assertTrue(SdkBehaviorAuditor.isSensitiveUrl("https://api.example.com/location?lat=51&lng=0"))
    }

    @Test
    fun `URL containing credential is flagged`() {
        assertTrue(SdkBehaviorAuditor.isSensitiveUrl("https://auth.example.com/credential/refresh"))
    }

    @Test
    fun `multiple SDKs summarised independently`() {
        SdkBehaviorAuditor.record("com.sdk.a", "GET", "https://safe.example.com/data")
        SdkBehaviorAuditor.record("com.sdk.b", "POST", "https://leak.example.com?advertising_id=xyz")
        val summaries = SdkBehaviorAuditor.summaries()
        assertEquals(2, summaries.size)
        // Flagged SDK should appear first (sorted by sensitive count descending)
        assertEquals("com.sdk.b", summaries[0].sdkTag)
        assertTrue(summaries[0].flagged)
        assertFalse(summaries[1].flagged)
    }

    @Test
    fun `addSensitiveKeyword extends detection`() {
        SdkBehaviorAuditor.addSensitiveKeyword("supersecret")
        assertTrue(SdkBehaviorAuditor.isSensitiveUrl("https://api.example.com/supersecret/v2"))
    }

    @Test
    fun `total requests counts all events for an SDK`() {
        repeat(5) { SdkBehaviorAuditor.record("com.repeat", "GET", "https://api.example.com/feed") }
        val summary = SdkBehaviorAuditor.summaries().first { it.sdkTag == "com.repeat" }
        assertEquals(5, summary.totalRequests)
    }

    @Test
    fun `reset clears all events`() {
        SdkBehaviorAuditor.record("com.sdk", "GET", "https://example.com")
        SdkBehaviorAuditor.reset()
        assertTrue(SdkBehaviorAuditor.summaries().isEmpty())
    }
}

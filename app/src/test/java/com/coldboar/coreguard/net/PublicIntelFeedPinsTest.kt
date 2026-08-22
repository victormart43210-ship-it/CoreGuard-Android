package com.coldboar.coreguard.net

import com.coldboar.coreguard.mvt.IocFeedFetcher
import com.quilla.intelligence.sdk.intel.PublicMultiSourceStixFetcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicIntelFeedPinsTest {

    @Test
    fun `default IOC feed is commit-pinned with digest`() {
        val pin = PublicIntelFeedPins.pinFor(IocFeedFetcher.DEFAULT_FEED_URL)
        assertNotNull(pin)
        assertEquals(PublicIntelFeedPins.PEGASUS.sha256Hex, pin!!.sha256Hex)
        assertFalse(PublicIntelFeedPins.isFloatingBranchUrl(IocFeedFetcher.DEFAULT_FEED_URL))
        assertFalse(IocFeedFetcher.DEFAULT_FEED_URL.contains("/master/"))
    }

    @Test
    fun `STIX research defaults reject floating branch refs`() {
        val urls = PublicMultiSourceStixFetcher.DEFAULT_FEEDS.map { it.url }
        assertTrue(urls.isNotEmpty())
        assertTrue(urls.none { PublicIntelFeedPins.isFloatingBranchUrl(it) })
        assertTrue(PublicMultiSourceStixFetcher.DEFAULT_FEEDS.all { it.sha256Hex.length == 64 })
    }

    @Test
    fun `floating branch detector catches master and main`() {
        assertTrue(
            PublicIntelFeedPins.isFloatingBranchUrl(
                "https://raw.githubusercontent.com/AmnestyTech/investigations/master/x.stix2"
            )
        )
        assertTrue(
            PublicIntelFeedPins.isFloatingBranchUrl(
                "https://raw.githubusercontent.com/MISP/misp-galaxy/main/clusters/android.json"
            )
        )
        assertFalse(
            PublicIntelFeedPins.isFloatingBranchUrl(PublicIntelFeedPins.MISP_ANDROID.url)
        )
    }

    @Test
    fun `digest mismatch is treated as integrity failure by sha helper`() {
        val bytes = "poison".toByteArray()
        val actual = HardenedHttpsDownloader.sha256Hex(bytes)
        assertFalse(actual.equals(PublicIntelFeedPins.PEGASUS.sha256Hex, ignoreCase = true))
    }
}

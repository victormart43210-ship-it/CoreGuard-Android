package com.coldboar.coreguard.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class HardenedHttpsDownloaderTest {

    @Test
    fun `host allowlist is exact or explicit wildcard`() {
        val allowed = setOf("raw.githubusercontent.com", "www.cisa.gov")
        assertTrue(HardenedHttpsDownloader.hostAllowed("raw.githubusercontent.com", allowed))
        assertFalse(
            "implicit subdomains are not accepted without wildcard policy",
            HardenedHttpsDownloader.hostAllowed("cdn.www.cisa.gov", allowed)
        )
        assertFalse(HardenedHttpsDownloader.hostAllowed("evil.com", allowed))
        assertFalse(HardenedHttpsDownloader.hostAllowed("githubusercontent.com.evil.com", allowed))
        val wildcard = setOf("*.cisa.gov")
        assertTrue(HardenedHttpsDownloader.hostAllowed("cdn.cisa.gov", wildcard))
    }

    @Test
    fun `readBounded rejects oversized stream before allocating past cap`() {
        val input = ByteArrayInputStream(ByteArray(100) { 1 })
        assertNull(HardenedHttpsDownloader.readBounded(input, maxBytes = 50))
    }

    @Test
    fun `readBounded accepts stream within cap`() {
        val payload = "hello-intel".toByteArray()
        val got = HardenedHttpsDownloader.readBounded(ByteArrayInputStream(payload), 64)
        assertNotNull(got)
        assertEquals("hello-intel", got!!.toString(Charsets.UTF_8))
    }

    @Test
    fun `sha256Hex is stable`() {
        val hex = HardenedHttpsDownloader.sha256Hex("abc".toByteArray())
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            hex
        )
    }

    @Test
    fun `download rejects cleartext without opening connection`() {
        val result = HardenedHttpsDownloader.download(
            "http://example.com/feed.json",
            HardenedHttpsDownloader.Policy(
                allowedHosts = setOf("example.com"),
                maxBytes = 1024,
                expectedSha256Hex = "00"
            )
        )
        assertTrue(result is HardenedHttpsDownloader.Result.Failure)
        assertTrue((result as HardenedHttpsDownloader.Result.Failure).reason.contains("HTTPS"))
    }

    @Test
    fun `download rejects host outside allowlist`() {
        val result = HardenedHttpsDownloader.download(
            "https://evil.example/feed.json",
            HardenedHttpsDownloader.Policy(
                allowedHosts = setOf("raw.githubusercontent.com"),
                maxBytes = 1024,
                expectedSha256Hex = "00"
            )
        )
        assertTrue(result is HardenedHttpsDownloader.Result.Failure)
        assertTrue((result as HardenedHttpsDownloader.Result.Failure).reason.contains("allowlist"))
    }
}

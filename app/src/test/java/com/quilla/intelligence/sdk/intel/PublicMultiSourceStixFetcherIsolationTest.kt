package com.quilla.intelligence.sdk.intel

import com.coldboar.coreguard.net.HardenedHttpsDownloader
import com.coldboar.coreguard.net.HttpTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * Per-feed isolation: one throwing/malformed/empty feed must not discard others.
 */
class PublicMultiSourceStixFetcherIsolationTest {

    private fun stixBundle(domain: String): ByteArray = """
        {"type":"bundle","id":"bundle--t","objects":[
          {"type":"indicator","id":"indicator--1","name":"$domain",
           "pattern":"[domain-name:value = '$domain']"}
        ]}
    """.trimIndent().toByteArray()

    private fun emptyBundle(): ByteArray =
        """{"type":"bundle","id":"bundle--e","objects":[]}""".toByteArray()

    private class MapTransport(
        private val byUrl: Map<String, () -> HttpTransport.Response>
    ) : HttpTransport {
        override fun get(
            url: String,
            connectTimeoutMs: Int,
            readTimeoutMs: Int,
            headers: Map<String, String>
        ): HttpTransport.Response = byUrl[url]?.invoke()
            ?: error("unexpected url $url")
    }

    @Test
    fun `feed exception does not discard other verified successes`() {
        val goodBody = stixBundle("ok.example")
        val goodSha = HardenedHttpsDownloader.sha256Hex(goodBody)
        val goodUrl = "https://raw.githubusercontent.com/org/good/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/g.stix2"
        val badUrl = "https://raw.githubusercontent.com/org/bad/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb/b.stix2"
        val transport = MapTransport(
            mapOf(
                goodUrl to {
                    HttpTransport.Response(200, body = ByteArrayInputStream(goodBody))
                },
                badUrl to {
                    throw RuntimeException("transport exploded")
                }
            )
        )
        val fetcher = PublicMultiSourceStixFetcher(
            feeds = listOf(
                PublicMultiSourceStixFetcher.Feed("Good", goodUrl, goodSha),
                PublicMultiSourceStixFetcher.Feed("Bad", badUrl, "11".repeat(32))
            ),
            transport = transport
        )
        val report = fetcher.fetchReport()
        assertEquals(1, report.verifiedSourceCount)
        assertEquals(1, report.failedSourceCount)
        assertEquals(1, report.indicators.size)
        assertEquals("ok.example", report.indicators.first().patternValue)
        assertTrue(report.sourceResults.any { it.name == "Bad" && !it.success })
        assertTrue(report.sourceResults.any { it.name == "Good" && it.success })
    }

    @Test
    fun `partial success preserves only independently verified sources`() {
        val a = stixBundle("a.example")
        val b = stixBundle("b.example")
        val aUrl = "https://raw.githubusercontent.com/org/a/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/a.stix2"
        val bUrl = "https://raw.githubusercontent.com/org/b/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb/b.stix2"
        val transport = MapTransport(
            mapOf(
                aUrl to { HttpTransport.Response(200, body = ByteArrayInputStream(a)) },
                bUrl to {
                    HttpTransport.Response(
                        200,
                        body = ByteArrayInputStream(b)
                    )
                }
            )
        )
        val fetcher = PublicMultiSourceStixFetcher(
            feeds = listOf(
                PublicMultiSourceStixFetcher.Feed("A", aUrl, HardenedHttpsDownloader.sha256Hex(a)),
                // Wrong digest → fail closed for B only
                PublicMultiSourceStixFetcher.Feed("B", bUrl, "00".repeat(32))
            ),
            transport = transport
        )
        val report = fetcher.fetchReport()
        assertTrue(report.indicators.any { it.patternValue == "a.example" })
        assertFalse(report.indicators.any { it.patternValue == "b.example" })
        assertEquals(1, report.verifiedSourceCount)
        assertEquals(1, report.failedSourceCount)
    }

    @Test
    fun `malformed input fails that source only`() {
        val good = stixBundle("keep.example")
        val goodUrl = "https://raw.githubusercontent.com/org/g/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/g.stix2"
        val badUrl = "https://raw.githubusercontent.com/org/m/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb/m.stix2"
        val malformed = "not-json{{{".toByteArray()
        val transport = MapTransport(
            mapOf(
                goodUrl to { HttpTransport.Response(200, body = ByteArrayInputStream(good)) },
                badUrl to { HttpTransport.Response(200, body = ByteArrayInputStream(malformed)) }
            )
        )
        val fetcher = PublicMultiSourceStixFetcher(
            feeds = listOf(
                PublicMultiSourceStixFetcher.Feed("Good", goodUrl, HardenedHttpsDownloader.sha256Hex(good)),
                PublicMultiSourceStixFetcher.Feed("Malformed", badUrl, HardenedHttpsDownloader.sha256Hex(malformed))
            ),
            transport = transport
        )
        val report = fetcher.fetchReport()
        // Malformed parses to empty → UNAVAILABLE for that source; good kept.
        assertEquals(1, report.indicators.size)
        assertTrue(report.sourceResults.any { it.name == "Malformed" && !it.success })
    }

    @Test
    fun `empty digest-valid bundle is unavailable source outcome`() {
        val empty = emptyBundle()
        val url = "https://raw.githubusercontent.com/org/e/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/e.stix2"
        val transport = MapTransport(
            mapOf(url to { HttpTransport.Response(200, body = ByteArrayInputStream(empty)) })
        )
        val fetcher = PublicMultiSourceStixFetcher(
            feeds = listOf(
                PublicMultiSourceStixFetcher.Feed("Empty", url, HardenedHttpsDownloader.sha256Hex(empty))
            ),
            transport = transport
        )
        val report = fetcher.fetchReport()
        assertTrue(report.allUnavailable)
        assertEquals(0, report.verifiedSourceCount)
        assertFalse(report.sourceResults.single().success)
        assertTrue(report.sourceResults.single().failureReason!!.contains("empty"))
        assertEquals(StixSourceResult.STATUS_UNAVAILABLE, report.sourceResults.single().status)
    }
}

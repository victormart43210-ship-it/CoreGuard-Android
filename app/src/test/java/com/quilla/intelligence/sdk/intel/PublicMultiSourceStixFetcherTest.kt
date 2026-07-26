package com.quilla.intelligence.sdk.intel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicMultiSourceStixFetcherTest {

    @Test
    fun `parseStixBundle extracts domain indicators with source feed`() {
        val stix = """
            {
              "type": "bundle",
              "id": "bundle--test",
              "objects": [
                {
                  "type": "indicator",
                  "id": "indicator--1",
                  "name": "evil",
                  "description": "Test IOC",
                  "pattern": "[domain-name:value = 'evil.example.com']"
                }
              ]
            }
        """.trimIndent()
        val parsed = PublicMultiSourceStixFetcher.parseStixBundle(
            json = stix,
            sourceFeed = "UnitTest Feed",
            ttlTimestamp = Long.MAX_VALUE
        )
        assertEquals(1, parsed.size)
        assertEquals("evil.example.com", parsed.first().patternValue)
        assertEquals("DOMAIN", parsed.first().indicatorType)
        assertEquals("UnitTest Feed", parsed.first().sourceFeed)
    }

    @Test
    fun `default feed list covers Amnesty and MVT campaign URLs`() {
        val urls = PublicMultiSourceStixFetcher.DEFAULT_FEEDS.map { it.url }
        assertTrue(urls.any { it.contains("AmnestyTech/investigations") })
        assertTrue(urls.any { it.contains("mvt-project/mvt-indicators") })
        assertTrue(urls.any { it.contains("novispy") || it.contains("darksword") })
        assertTrue(urls.none { it.contains("indicators/pegasus.stix2") })
        assertTrue(urls.all { it.startsWith("https://") })
    }

    @Test
    fun `cleartext feed URLs are rejected`() {
        val fetcher = PublicMultiSourceStixFetcher(
            feeds = listOf(
                PublicMultiSourceStixFetcher.Feed("bad", "http://example.com/feed.stix2")
            )
        )
        assertTrue(fetcher.fetchAllSources().isEmpty())
    }
}

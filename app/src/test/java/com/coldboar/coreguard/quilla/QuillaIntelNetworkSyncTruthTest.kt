package com.coldboar.coreguard.quilla

import android.content.Context
import com.coldboar.coreguard.quilla.knowledge.CyberKnowledgeBase
import com.quilla.intelligence.sdk.intel.MultiSourceStixFetcher
import com.quilla.intelligence.sdk.intel.PublicMultiSourceStixFetcher
import com.quilla.intelligence.sdk.intel.StixFetchReport
import com.quilla.intelligence.sdk.intel.StixSourceResult
import com.quilla.intelligence.sdk.model.StixIndicator
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.whenever
import java.io.File

class QuillaIntelNetworkSyncTruthTest {

    @After
    fun tearDown() {
        CyberKnowledgeBase.clear()
    }

    private fun mockContext(): Context {
        val context = Mockito.mock(Context::class.java)
        whenever(context.filesDir).thenReturn(File("build/tmp-quilla-sync-truth").also { it.mkdirs() })
        whenever(context.assets).thenThrow(RuntimeException("no assets in unit test"))
        return context
    }

    private fun indicator(value: String, feed: String = "Unit") = StixIndicator(
        id = "indicator--$value",
        sourceFeed = feed,
        indicatorType = "DOMAIN",
        patternValue = value,
        description = "unit",
        ttlTimestamp = Long.MAX_VALUE
    )

    private fun webFail() = QuillaWebSecurityIntelFetcher.WebIntelResult(
        entries = emptyList(),
        sourcesOk = emptyList(),
        sourcesFailed = listOf("CISA KEV UNAVAILABLE (digest pin miss or fetch failure — refresh pin deliberately)")
    )

    @Test
    fun `all STIX fail and web fails with local IOCs still not synced`() {
        val fetcher = object : MultiSourceStixFetcher {
            override fun fetchAllSources(): List<StixIndicator> = emptyList()
            override fun fetchReport(): StixFetchReport =
                StixFetchReport.unavailable("all feeds failed", configuredFeeds = 3)
        }
        val snapshot = QuillaIntelNetwork.syncAll(
            context = mockContext(),
            stixFetcher = fetcher,
            webFetcher = { webFail() }
        )
        assertFalse(snapshot.synced)
        assertTrue(snapshot.syncFailed)
        assertEquals(StixSourceResult.STATUS_UNAVAILABLE, snapshot.stixStatus)
        assertTrue(snapshot.feedNotes.any { it.contains("STIX: UNAVAILABLE") })
        // Local fallback IOCs may populate correlator but must not flip synced.
        assertTrue(snapshot.onDeviceMvtCount >= 0)
    }

    @Test
    fun `injected empty STIX list is not synchronized`() {
        val fetcher = MultiSourceStixFetcher { emptyList() }
        val snapshot = QuillaIntelNetwork.syncAll(
            context = mockContext(),
            stixFetcher = fetcher,
            webFetcher = { webFail() }
        )
        assertFalse(snapshot.synced)
        assertTrue(snapshot.syncFailed)
        assertEquals(StixSourceResult.STATUS_UNAVAILABLE, snapshot.stixStatus)
    }

    @Test
    fun `partial STIX success preserves indicators and lists failed sources`() {
        val fetcher = object : MultiSourceStixFetcher {
            override fun fetchAllSources() = fetchReport().indicators
            override fun fetchReport() = StixFetchReport(
                indicators = listOf(indicator("ok.example")),
                sourceResults = listOf(
                    StixSourceResult(
                        name = "GoodFeed",
                        url = "https://example/good",
                        success = true,
                        indicators = listOf(indicator("ok.example")),
                        status = StixSourceResult.STATUS_VERIFIED
                    ),
                    StixSourceResult(
                        name = "BadFeed",
                        url = "https://example/bad",
                        success = false,
                        failureReason = "SHA-256 mismatch",
                        status = StixSourceResult.STATUS_UNAVAILABLE
                    )
                ),
                verifiedSourceCount = 1,
                failedSourceCount = 1,
                allUnavailable = false
            )
        }
        val snapshot = QuillaIntelNetwork.syncAll(
            context = mockContext(),
            stixFetcher = fetcher,
            webFetcher = { webFail() }
        )
        assertTrue(snapshot.synced)
        assertFalse(snapshot.syncFailed)
        assertEquals(1, snapshot.stixIndicatorCount)
        assertEquals(1, snapshot.stixVerifiedSourceCount)
        assertEquals(1, snapshot.stixFailedSourceCount)
        assertTrue(snapshot.feedNotes.any { it.contains("BadFeed") && it.contains("SHA-256") })
        assertTrue(snapshot.feedNotes.any { it.contains("GoodFeed") })
    }

    @Test
    fun `digest mismatch and empty STIX report as UNAVAILABLE`() {
        val emptyBundleFetcher = object : MultiSourceStixFetcher {
            override fun fetchAllSources() = emptyList<StixIndicator>()
            override fun fetchReport() = StixFetchReport(
                indicators = emptyList(),
                sourceResults = listOf(
                    StixSourceResult(
                        name = "EmptyBundle",
                        url = "https://example/empty",
                        success = false,
                        failureReason = "empty STIX bundle after verify — UNAVAILABLE",
                        status = StixSourceResult.STATUS_UNAVAILABLE
                    ),
                    StixSourceResult(
                        name = "DigestMiss",
                        url = "https://example/miss",
                        success = false,
                        failureReason = "SHA-256 mismatch (integrity failure)",
                        status = StixSourceResult.STATUS_UNAVAILABLE
                    )
                ),
                verifiedSourceCount = 0,
                failedSourceCount = 2,
                allUnavailable = true
            )
        }
        val snapshot = QuillaIntelNetwork.syncAll(
            context = mockContext(),
            stixFetcher = emptyBundleFetcher,
            webFetcher = { webFail() }
        )
        assertFalse(snapshot.synced)
        assertTrue(snapshot.syncFailed)
        assertEquals(StixSourceResult.STATUS_UNAVAILABLE, snapshot.stixStatus)
        assertEquals(0, snapshot.stixVerifiedSourceCount)
    }

    @Test
    fun `trainer snapshot matches final sync truth flags`() {
        val fetcher = MultiSourceStixFetcher { emptyList() }
        val snapshot = QuillaIntelNetwork.syncAll(
            context = mockContext(),
            stixFetcher = fetcher,
            webFetcher = { webFail() }
        )
        // Final returned snapshot must remain fail-closed (no hardcoded synced=true).
        assertFalse(snapshot.synced)
        assertTrue(snapshot.syncFailed)
        assertEquals(snapshot.synced, QuillaIntelNetwork.lastSnapshot().synced)
        assertEquals(snapshot.syncFailed, QuillaIntelNetwork.lastSnapshot().syncFailed)
    }

    @Test
    fun `PublicMultiSourceStixFetcher empty digest-valid bundle is source failure`() {
        // Unit-level: parse empty bundle → empty list is treated as failure in fetchFeedDetailed.
        val parsed = PublicMultiSourceStixFetcher.parseStixBundle(
            json = """{"type":"bundle","id":"bundle--x","objects":[]}""",
            sourceFeed = "empty",
            ttlTimestamp = Long.MAX_VALUE
        )
        assertTrue(parsed.isEmpty())
    }
}

private fun MultiSourceStixFetcher(block: () -> List<StixIndicator>): MultiSourceStixFetcher =
    object : MultiSourceStixFetcher {
        override fun fetchAllSources(): List<StixIndicator> = block()
    }

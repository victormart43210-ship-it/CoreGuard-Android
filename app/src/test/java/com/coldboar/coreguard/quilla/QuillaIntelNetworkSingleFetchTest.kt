package com.coldboar.coreguard.quilla

import android.content.Context
import com.coldboar.coreguard.quilla.knowledge.CyberKnowledgeBase
import com.quilla.intelligence.sdk.engine.SlidingWindowCorrelationEngine
import com.quilla.intelligence.sdk.intel.MultiSourceStixFetcher
import com.quilla.intelligence.sdk.intel.StixFetchReport
import com.quilla.intelligence.sdk.intel.StixSourceResult
import com.quilla.intelligence.sdk.model.StixIndicator
import com.coreguard.android.data.local.dao.QuillaLearningDao
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.whenever
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Single-fetch Quilla synchronization: no second STIX pull; sync truth requires
 * typed successful source + verified merged indicators.
 */
class QuillaIntelNetworkSingleFetchTest {

    @After
    fun tearDown() {
        CyberKnowledgeBase.clear()
    }

    private fun mockContext(): Context {
        val context = Mockito.mock(Context::class.java)
        whenever(context.filesDir).thenReturn(File("build/tmp-quilla-single-fetch").also { it.mkdirs() })
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
        sourcesFailed = listOf("UNAVAILABLE")
    )

    @Test
    fun `double-fetch is prevented — fetchReport called once per sync`() {
        val fetches = AtomicInteger(0)
        val fetcher = object : MultiSourceStixFetcher {
            override fun fetchAllSources(): List<StixIndicator> {
                fetches.incrementAndGet()
                return listOf(indicator("once.example"))
            }
            override fun fetchReport(): StixFetchReport {
                fetches.incrementAndGet()
                return StixFetchReport(
                    indicators = listOf(indicator("once.example")),
                    sourceResults = listOf(
                        StixSourceResult(
                            name = "Once",
                            url = "https://example/once",
                            success = true,
                            indicators = listOf(indicator("once.example")),
                            status = StixSourceResult.STATUS_VERIFIED
                        )
                    ),
                    verifiedSourceCount = 1,
                    failedSourceCount = 0,
                    allUnavailable = false
                )
            }
        }
        QuillaIntelNetwork.syncAll(mockContext(), fetcher, webFetcher = { webFail() })
        // Only fetchReport — never fetchAllSources via sliding-window second pull.
        assertEquals(1, fetches.get())
    }

    @Test
    fun `stale fetcher result is not retained by correlation engine load path`() {
        val dao = Mockito.mock(QuillaLearningDao::class.java)
        val calls = AtomicInteger(0)
        val fetcher = object : MultiSourceStixFetcher {
            override fun fetchAllSources(): List<StixIndicator> {
                calls.incrementAndGet()
                return listOf(indicator("stale.example"))
            }
            override fun fetchReport() = StixFetchReport.fromLegacyList(fetchAllSources())
        }
        val engine = SlidingWindowCorrelationEngine(dao, fetcher)
        engine.loadVerifiedIndicators(listOf(indicator("fresh.example")))
        assertEquals(0, calls.get())
        engine.syncThreatFeeds()
        assertEquals(1, calls.get())
    }

    @Test
    fun `inconsistent counters are not trusted — derive from sourceResults`() {
        val fetcher = object : MultiSourceStixFetcher {
            override fun fetchAllSources() = emptyList<StixIndicator>()
            override fun fetchReport() = StixFetchReport(
                indicators = emptyList(),
                sourceResults = listOf(
                    StixSourceResult(
                        name = "Lie",
                        url = "",
                        success = false,
                        failureReason = "failed",
                        status = StixSourceResult.STATUS_UNAVAILABLE
                    )
                ),
                // Caller-provided counters claim success — must not sync.
                verifiedSourceCount = 99,
                failedSourceCount = 0,
                allUnavailable = false
            )
        }
        val snapshot = QuillaIntelNetwork.syncAll(mockContext(), fetcher, webFetcher = { webFail() })
        assertFalse(snapshot.synced)
        assertEquals(0, snapshot.stixVerifiedSourceCount)
        assertEquals(1, snapshot.stixFailedSourceCount)
    }

    @Test
    fun `empty result is not successful remote synchronization`() {
        val fetcher = object : MultiSourceStixFetcher {
            override fun fetchAllSources() = emptyList<StixIndicator>()
            override fun fetchReport() = StixFetchReport(
                indicators = emptyList(),
                sourceResults = listOf(
                    StixSourceResult(
                        name = "EmptyOk",
                        url = "",
                        success = true,
                        indicators = emptyList(),
                        status = StixSourceResult.STATUS_VERIFIED
                    )
                ),
                verifiedSourceCount = 1,
                failedSourceCount = 0,
                allUnavailable = false
            )
        }
        val snapshot = QuillaIntelNetwork.syncAll(mockContext(), fetcher, webFetcher = { webFail() })
        assertFalse(snapshot.synced)
        assertTrue(snapshot.syncFailed)
    }

    @Test
    fun `partial success syncs when at least one verified indicator exists`() {
        val fetcher = object : MultiSourceStixFetcher {
            override fun fetchAllSources() = fetchReport().indicators
            override fun fetchReport() = StixFetchReport(
                indicators = listOf(indicator("ok.example")),
                sourceResults = listOf(
                    StixSourceResult(
                        name = "Good",
                        url = "https://example/good",
                        success = true,
                        indicators = listOf(indicator("ok.example")),
                        status = StixSourceResult.STATUS_VERIFIED
                    ),
                    StixSourceResult(
                        name = "Bad",
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
        val snapshot = QuillaIntelNetwork.syncAll(mockContext(), fetcher, webFetcher = { webFail() })
        assertTrue(snapshot.synced)
        assertEquals(1, snapshot.stixIndicatorCount)
    }

    @Test
    fun `concurrent sync invocations each perform a single fetch`() {
        val fetches = AtomicInteger(0)
        val fetcher = object : MultiSourceStixFetcher {
            override fun fetchAllSources() = error("must not double-fetch")
            override fun fetchReport(): StixFetchReport {
                fetches.incrementAndGet()
                Thread.sleep(20)
                return StixFetchReport(
                    indicators = listOf(indicator("c.example")),
                    sourceResults = listOf(
                        StixSourceResult(
                            name = "C",
                            url = "",
                            success = true,
                            indicators = listOf(indicator("c.example")),
                            status = StixSourceResult.STATUS_VERIFIED
                        )
                    ),
                    verifiedSourceCount = 1,
                    failedSourceCount = 0,
                    allUnavailable = false
                )
            }
        }
        val pool = Executors.newFixedThreadPool(4)
        val start = CountDownLatch(1)
        val done = CountDownLatch(4)
        repeat(4) {
            pool.execute {
                start.await()
                QuillaIntelNetwork.syncAll(mockContext(), fetcher, webFetcher = { webFail() })
                done.countDown()
            }
        }
        start.countDown()
        assertTrue(done.await(30, TimeUnit.SECONDS))
        pool.shutdown()
        assertEquals(4, fetches.get())
    }
}

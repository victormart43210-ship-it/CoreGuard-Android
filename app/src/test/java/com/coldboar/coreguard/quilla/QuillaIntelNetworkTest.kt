package com.coldboar.coreguard.quilla

import android.content.Context
import com.coldboar.coreguard.quilla.knowledge.CyberKnowledgeBase
import com.quilla.intelligence.sdk.intel.MultiSourceStixFetcher
import com.quilla.intelligence.sdk.model.StixIndicator
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.whenever
import java.io.File

class QuillaIntelNetworkTest {

    @After
    fun tearDown() {
        CyberKnowledgeBase.clear()
    }

    @Test
    fun `syncAll merges STIX into correlator and web knowledge into codex`() {
        val context = Mockito.mock(Context::class.java)
        whenever(context.filesDir).thenReturn(File("build/tmp-quilla-intel-test").also { it.mkdirs() })
        whenever(context.assets).thenThrow(RuntimeException("no assets in unit test"))

        val stixFetcher = MultiSourceStixFetcher {
            listOf(
                StixIndicator(
                    id = "indicator--net",
                    sourceFeed = "UnitTest",
                    indicatorType = "DOMAIN",
                    patternValue = "c2.evil.test",
                    description = "unit",
                    ttlTimestamp = Long.MAX_VALUE
                )
            )
        }

        CyberKnowledgeBase.clear()
        val snapshot = QuillaIntelNetwork.syncAll(
            context = context,
            stixFetcher = stixFetcher,
            webFetcher = {
                QuillaWebSecurityIntelFetcher.WebIntelResult(
                    entries = listOf(
                        CyberKnowledgeBase.Entry(
                            id = "kev-cve-test",
                            title = "CVE-TEST — Android test vuln",
                            category = "web-intel-kev",
                            tags = setOf("android", "kev", "cve-test"),
                            summary = "test",
                            body = "body",
                            defense = "patch",
                            references = emptyList()
                        )
                    ),
                    sourcesOk = listOf("CISA KEV (1 Android-relevant)"),
                    sourcesFailed = emptyList()
                )
            }
        )

        assertTrue(snapshot.synced)
        assertFalse(snapshot.syncFailed)
        assertEquals(1, snapshot.stixIndicatorCount)
        assertEquals(1, snapshot.webKnowledgeCount)
        assertTrue(QuillaMemoryModule.correlationEngine().indicatorCount() >= 1)
        assertTrue(CyberKnowledgeBase.search("CVE-TEST Android", limit = 2).isNotEmpty())
    }
}

/** SAM helper so tests can construct a fetcher without a full class. */
private fun MultiSourceStixFetcher(block: () -> List<StixIndicator>): MultiSourceStixFetcher =
    object : MultiSourceStixFetcher {
        override fun fetchAllSources(): List<StixIndicator> = block()
    }

package com.quilla.intelligence.sdk.engine

import com.coreguard.android.data.local.dao.QuillaLearningDao
import com.coreguard.android.data.local.entity.QuillaHypothesisEntity
import com.quilla.intelligence.sdk.intel.MultiSourceStixFetcher
import com.quilla.intelligence.sdk.model.StixIndicator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify

@OptIn(ExperimentalCoroutinesApi::class)
class SlidingWindowCorrelationEngineTest {

    @Mock
    private lateinit var mockDao: QuillaLearningDao

    @Mock
    private lateinit var mockStixFetcher: MultiSourceStixFetcher

    private lateinit var engine: SlidingWindowCorrelationEngine

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        engine = SlidingWindowCorrelationEngine(mockDao, mockStixFetcher)
    }

    @Test
    fun `pushEvent triggers high confidence threat hypothesis when multiple signals correlate`() = runTest {
        val targetPackage = "com.suspicious.app"
        val hypothesisCaptor = argumentCaptor<QuillaHypothesisEntity>()

        // 1. Ingest initial dynamic code loading signal
        engine.pushEvent(
            RawEvent(
                packageName = targetPackage,
                type = "RASP_DCL",
                detail = "Loaded DEX file from /sdcard/payload.dex"
            )
        )

        // 2. Ingest root signal
        engine.pushEvent(
            RawEvent(
                packageName = targetPackage,
                type = "RASP_ROOT",
                detail = "su binary detected in /system/xbin"
            )
        )

        // 3. Ingest network outbound signal (pushes confidence over 0.75 threshold)
        engine.pushEvent(
            RawEvent(
                packageName = targetPackage,
                type = "NETWORK_OUTBOUND",
                detail = "DEST:malicious-c2.com,UNTRUSTED_AP"
            )
        )

        // Verify hypothesis was saved to persistent storage
        verify(mockDao).insertHypothesis(hypothesisCaptor.capture())

        val generatedHypothesis = hypothesisCaptor.firstValue
        assertNotNull(generatedHypothesis)
        assertEquals("BEHAVIORAL_ANOMALY", generatedHypothesis.hypothesisType)
        assertTrue(generatedHypothesis.confidence >= 0.75f)
        assertTrue(generatedHypothesis.summary.contains(targetPackage))
    }

    @Test
    fun `syncThreatFeeds matches outbound connection against STIX indicators`() = runTest {
        val targetPackage = "com.spyware.target"
        val c2Domain = "pegasus-c2-server.com"
        val now = System.currentTimeMillis()
        val hypothesisCaptor = argumentCaptor<QuillaHypothesisEntity>()

        val mockIndicator = StixIndicator(
            id = "indicator--1234",
            sourceFeed = "Amnesty International",
            indicatorType = "DOMAIN",
            patternValue = c2Domain,
            description = "Pegasus infrastructure domain",
            ttlTimestamp = now + 86400000L // Active rule
        )

        `when`(mockStixFetcher.fetchAllSources()).thenReturn(listOf(mockIndicator))

        // Synchronize threat intelligence feeds
        engine.syncThreatFeeds()

        // Push network event targeting the malicious C2 domain
        engine.pushEvent(
            RawEvent(
                packageName = targetPackage,
                type = "RASP_DCL",
                detail = "Dynamic loading"
            )
        )

        engine.pushEvent(
            RawEvent(
                packageName = targetPackage,
                type = "NETWORK_OUTBOUND",
                detail = "DEST:$c2Domain,UNTRUSTED_AP"
            )
        )

        verify(mockDao).insertHypothesis(hypothesisCaptor.capture())

        val hypothesis = hypothesisCaptor.firstValue
        assertEquals("STIX_THREAT_MATCH", hypothesis.hypothesisType)
        assertTrue(hypothesis.confidence >= 0.70f)
        assertTrue(hypothesis.evidenceJson.contains(c2Domain))
    }

    @Test
    fun `expired events outside the 5-minute window are evicted and do not trigger alert`() = runTest {
        val targetPackage = "com.innocent.app"
        val oldTimestamp = System.currentTimeMillis() - (6 * 60 * 1000L) // 6 minutes ago

        // Ingest stale event outside sliding window
        engine.pushEvent(
            RawEvent(
                packageName = targetPackage,
                type = "RASP_DCL",
                detail = "Old loading event",
                timestamp = oldTimestamp
            )
        )

        // Ingest single fresh root event (Confidence: 0.40 + 0.20 = 0.60 < 0.75 threshold)
        engine.pushEvent(
            RawEvent(
                packageName = targetPackage,
                type = "RASP_ROOT",
                detail = "Root check",
                timestamp = System.currentTimeMillis()
            )
        )

        // Verify no hypothesis was generated because old event was evicted
        verify(mockDao, never()).insertHypothesis(any())
    }

    @Test
    fun `threatEvents Flow emits hypothesis to active observers`() = runTest {
        val targetPackage = "com.monitored.app"
        var emittedHypothesis: QuillaHypothesisEntity? = null

        // Collect emitted threat events asynchronously
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            emittedHypothesis = engine.threatEvents.first()
        }

        // Trigger threshold breach
        engine.pushEvent(RawEvent(targetPackage, "RASP_DCL", "DCL"))
        engine.pushEvent(RawEvent(targetPackage, "RASP_ROOT", "ROOT"))
        engine.pushEvent(RawEvent(targetPackage, "NETWORK_OUTBOUND", "DEST:bad.com,UNTRUSTED_AP"))

        assertNotNull(emittedHypothesis)
        assertEquals(targetPackage, emittedHypothesis?.summary?.let {
            if (it.contains(targetPackage)) targetPackage else null
        })

        job.cancel()
    }
}

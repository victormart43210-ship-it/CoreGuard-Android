package com.coldboar.coreguard.quilla

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class QuillaCorrelationEngineTest {

    private lateinit var store: QuillaHypothesisStore
    private lateinit var engine: QuillaCorrelationEngine

    private val knownIoc = AmnestyIndicator(
        id = "indicator--test-001",
        indicatorType = "DOMAIN",
        patternValue = "evil.example.com",
        description = "Test Amnesty IOC"
    )

    @Before
    fun setUp() {
        store = QuillaHypothesisStore()
        // Use an offline fetcher that never touches the network.
        engine = QuillaCorrelationEngine(store, fetcher = { emptyList() })
        engine.loadIndicators(listOf(knownIoc))
    }

    // ── IOC matching ──────────────────────────────────────────────────────────

    @Test
    fun `IOC domain match alone pushes score to 0_90 and generates hypothesis`() {
        engine.correlateSignals(
            packageName = "com.test.app",
            rasp = null,
            network = NetworkEvent(
                packageName = "com.test.app",
                destinationDomainOrIp = "evil.example.com",
                isUntrustedNetwork = false,
                bytesTransferred = 1024L
            )
        )
        val hypotheses = store.all()
        assertEquals(1, hypotheses.size)
        val h = hypotheses.first()
        assertEquals("AMNESTY_IOC_BEHAVIORAL_MATCH", h.hypothesisType)
        assertEquals("ACTIVE", h.status)
        assertEquals(0.90f, h.confidence, 0.001f) // 0.50 + 0.40
        assertTrue(h.evidenceJson.contains("evil.example.com"))
    }

    @Test
    fun `no signals below threshold produce no hypothesis`() {
        // Base score 0.50, untrusted network +0.10 = 0.60 — below 0.75 threshold.
        engine.correlateSignals(
            packageName = "com.safe.app",
            rasp = null,
            network = NetworkEvent(
                packageName = "com.safe.app",
                destinationDomainOrIp = "trusted.example.com",
                isUntrustedNetwork = true,
                bytesTransferred = 512L
            )
        )
        assertTrue("Expected no hypothesis below threshold", store.all().isEmpty())
    }

    @Test
    fun `DCL plus root cross threshold without IOC match`() {
        // 0.50 + 0.25 (DCL) + 0.20 (root) = 0.95
        engine.correlateSignals(
            packageName = "com.suspicious.app",
            rasp = RaspEvent(
                packageName = "com.suspicious.app",
                isDynamicCodeLoaded = true,
                isRootDetected = true
            ),
            network = null
        )
        val hypotheses = store.all()
        assertEquals(1, hypotheses.size)
        val h = hypotheses.first()
        assertEquals(0.95f, h.confidence, 0.001f)
        assertTrue(h.evidenceJson.contains("Dynamic Code Loading"))
        assertTrue(h.evidenceJson.contains("root"))
    }

    @Test
    fun `confidence is clamped to 1_0 when all signals fire`() {
        // 0.50 + 0.40 + 0.25 + 0.20 + 0.10 = 1.45 → clamped to 1.0
        engine.correlateSignals(
            packageName = "com.max.app",
            rasp = RaspEvent(
                packageName = "com.max.app",
                isDynamicCodeLoaded = true,
                isRootDetected = true
            ),
            network = NetworkEvent(
                packageName = "com.max.app",
                destinationDomainOrIp = "evil.example.com",
                isUntrustedNetwork = true,
                bytesTransferred = 4096L
            )
        )
        val h = store.all().first()
        assertEquals(1.0f, h.confidence, 0.001f)
    }

    @Test
    fun `IOC match is case-insensitive`() {
        engine.correlateSignals(
            packageName = "com.test.app",
            rasp = null,
            network = NetworkEvent(
                packageName = "com.test.app",
                destinationDomainOrIp = "EVIL.EXAMPLE.COM",
                isUntrustedNetwork = false,
                bytesTransferred = 0L
            )
        )
        assertEquals(1, store.all().size)
    }

    @Test
    fun `unknown domain does not match IOC`() {
        engine.correlateSignals(
            packageName = "com.test.app",
            rasp = null,
            network = NetworkEvent(
                packageName = "com.test.app",
                destinationDomainOrIp = "benign.example.com",
                isUntrustedNetwork = false,
                bytesTransferred = 0L
            )
        )
        assertTrue(store.all().isEmpty())
    }

    // ── Hypothesis store ──────────────────────────────────────────────────────

    @Test
    fun `each correlateSignals call with threshold met adds a separate hypothesis`() {
        val network = NetworkEvent("com.a", "evil.example.com", false, 100L)
        engine.correlateSignals("com.a", null, network)
        engine.correlateSignals("com.b", null, network.copy(packageName = "com.b"))
        assertEquals(2, store.all().size)
    }

    @Test
    fun `store upsert replaces existing hypothesis with same id`() {
        val original = QuillaHypothesis("id-1", "TYPE", "summary", "{}", 0.8f, "ACTIVE")
        val updated = original.copy(status = "RESOLVED")
        store.upsert(original)
        store.upsert(updated)
        val all = store.all()
        assertEquals(1, all.size)
        assertEquals("RESOLVED", all.first().status)
    }

    @Test
    fun `store clear removes all hypotheses`() {
        store.upsert(QuillaHypothesis("id-1", "T", "s", "{}", 0.9f, "ACTIVE"))
        store.clear()
        assertTrue(store.all().isEmpty())
    }

    // ── STIX2 parser ─────────────────────────────────────────────────────────

    @Test
    fun `parseStixBundle extracts domain indicator`() {
        val stix = """
            {
              "type": "bundle",
              "objects": [
                {
                  "type": "indicator",
                  "id": "indicator--abc",
                  "pattern": "[domain-name:value = 'c2.evil.net']",
                  "description": "C2 domain"
                }
              ]
            }
        """.trimIndent()
        val result = AmnestyThreatIntelFetcher.parseStixBundle(stix)
        assertEquals(1, result.size)
        assertEquals("DOMAIN", result[0].indicatorType)
        assertEquals("c2.evil.net", result[0].patternValue)
        assertEquals("C2 domain", result[0].description)
    }

    @Test
    fun `parseStixBundle returns empty list for non-bundle JSON`() {
        val result = AmnestyThreatIntelFetcher.parseStixBundle("""{"type":"indicator"}""")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseStixBundle skips non-indicator objects`() {
        val stix = """
            {
              "type": "bundle",
              "objects": [
                { "type": "malware", "id": "malware--xyz" }
              ]
            }
        """.trimIndent()
        assertTrue(AmnestyThreatIntelFetcher.parseStixBundle(stix).isEmpty())
    }

    @Test
    fun `parseStixBundle classifies IP indicator type`() {
        val stix = """
            {
              "type": "bundle",
              "objects": [
                {
                  "type": "indicator",
                  "id": "indicator--ip1",
                  "pattern": "[ipv4-addr:value = '1.2.3.4']"
                }
              ]
            }
        """.trimIndent()
        val result = AmnestyThreatIntelFetcher.parseStixBundle(stix)
        assertEquals("IP", result[0].indicatorType)
        assertEquals("1.2.3.4", result[0].patternValue)
    }

    @Test
    fun `loadIndicators replaces prior IOC list`() {
        val replacement = AmnestyIndicator("id-new", "IP", "9.9.9.9", "Replacement IOC")
        engine.loadIndicators(listOf(replacement))
        // Old IOC (evil.example.com) should no longer match.
        engine.correlateSignals(
            packageName = "com.test.app",
            rasp = null,
            network = NetworkEvent("com.test.app", "evil.example.com", false, 0L)
        )
        assertTrue("Old IOC should no longer be active", store.all().isEmpty())
    }
}

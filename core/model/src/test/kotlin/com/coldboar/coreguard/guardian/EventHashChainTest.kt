package com.coldboar.coreguard.guardian

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventHashChainTest {

    @Test
    fun chainValidatesAndDetectsBreak() {
        val p0 = EventHashChain.canonicalPayload("a", 1, FindingCategory.DEBUGGING, Severity.INFORMATIONAL, "t", "d")
        val h0 = EventHashChain.hash(p0, null)
        val e0 = SecurityEvent("a", 1, 1, FindingCategory.DEBUGGING, Severity.INFORMATIONAL, EvidenceClass.OBSERVED, "t", "x", null, emptyList(), "d", h0, null)
        val p1 = EventHashChain.canonicalPayload("b", 2, FindingCategory.DEBUGGING, Severity.INFORMATIONAL, "t2", "d")
        val h1 = EventHashChain.hash(p1, h0)
        val e1 = SecurityEvent("b", 2, 2, FindingCategory.DEBUGGING, Severity.INFORMATIONAL, EvidenceClass.OBSERVED, "t2", "x", null, emptyList(), "d", h1, h0)
        assertTrue(EventHashChain.validateChain(listOf(e0, e1)))
        val broken = e1.copy(eventHash = "deadbeef")
        assertFalse(EventHashChain.validateChain(listOf(e0, broken)))
    }
}

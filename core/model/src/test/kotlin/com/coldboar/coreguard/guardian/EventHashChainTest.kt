package com.coldboar.coreguard.guardian

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventHashChainTest {

    private fun event(
        id: String,
        occurredAt: Long,
        previous: String?,
        title: String = "t-$id"
    ): SecurityEvent {
        val payload = EventHashChain.canonicalPayload(
            id = id,
            occurredAtEpochMillis = occurredAt,
            category = FindingCategory.DEBUGGING,
            severity = Severity.INFORMATIONAL,
            title = title,
            sourceDetector = "det-$id"
        )
        val hash = EventHashChain.hash(payload, previous)
        return SecurityEvent(
            id = id,
            occurredAtEpochMillis = occurredAt,
            detectedAtEpochMillis = occurredAt,
            category = FindingCategory.DEBUGGING,
            severity = Severity.INFORMATIONAL,
            evidenceClass = EvidenceClass.OBSERVED,
            title = title,
            explanation = "x",
            relatedPackageName = null,
            evidenceIds = emptyList(),
            sourceDetector = "det-$id",
            eventHash = hash,
            previousEventHash = previous
        )
    }

    @Test
    fun chainValidatesAndDetectsBreak() {
        val e0 = event("a", 1, null)
        val e1 = event("b", 2, e0.eventHash)
        assertTrue(EventHashChain.validateChain(listOf(e0, e1)))
        assertFalse(EventHashChain.validateChain(listOf(e0, e1.copy(eventHash = "deadbeef"))))
    }

    @Test
    fun tiedTimestampsValidateInAppendOrder() {
        val e0 = event("a", 100, null)
        val e1 = event("b", 100, e0.eventHash)
        val e2 = event("c", 100, e1.eventHash)
        assertTrue(EventHashChain.validateChain(listOf(e0, e1, e2)))
    }

    @Test
    fun nonMonotonicTimestampsValidateInAppendOrder() {
        val e0 = event("a", 5000, null)
        val e1 = event("b", 1000, e0.eventHash)
        val e2 = event("c", 3000, e1.eventHash)
        assertTrue(EventHashChain.validateChain(listOf(e0, e1, e2)))
    }

    @Test
    fun removingMiddleEventFailsValidation() {
        val e0 = event("a", 1, null)
        val e1 = event("b", 2, e0.eventHash)
        val e2 = event("c", 3, e1.eventHash)
        assertFalse(EventHashChain.validateChain(listOf(e0, e2)))
    }

    @Test
    fun modifyingPayloadFieldsFailsValidation() {
        val e0 = event("a", 1, null)
        val e1 = event("b", 2, e0.eventHash)
        val tampered = e1.copy(title = "tampered-title")
        assertFalse(EventHashChain.validateChain(listOf(e0, tampered)))
    }

    @Test
    fun modifyingPreviousEventHashFailsValidation() {
        val e0 = event("a", 1, null)
        val e1 = event("b", 2, e0.eventHash)
        val tampered = e1.copy(previousEventHash = "forged-prev")
        assertFalse(EventHashChain.validateChain(listOf(e0, tampered)))
    }

    @Test
    fun prefixRetentionWithoutCheckpointFailsValidation() {
        // Documents why v1 cannot prune ancestors: first surviving event still
        // points at a deleted previous hash, which validateChain rejects.
        val e0 = event("a", 1, null)
        val e1 = event("b", 2, e0.eventHash)
        val e2 = event("c", 3, e1.eventHash)
        assertTrue(EventHashChain.validateChain(listOf(e0, e1, e2)))
        assertFalse(
            "Surviving tail without checkpoint must not validate",
            EventHashChain.validateChain(listOf(e2))
        )
        assertEquals(e1.eventHash, e2.previousEventHash)
    }

    @Test
    fun emptyChainIsValid() {
        assertTrue(EventHashChain.validateChain(emptyList()))
    }
}

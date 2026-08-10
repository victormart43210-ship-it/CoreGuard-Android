package com.coldboar.coreguard.guardian

import java.security.MessageDigest

/**
 * Tamper-evident hash chain for Book of Changes events (Blueprint §9.5).
 * This is evidence of tampering, not prevention.
 */
object EventHashChain {

    fun canonicalPayload(
        id: String,
        occurredAtEpochMillis: Long,
        category: FindingCategory,
        severity: Severity,
        title: String,
        sourceDetector: String
    ): String =
        listOf(
            id,
            occurredAtEpochMillis.toString(),
            category.name,
            severity.name,
            title,
            sourceDetector
        ).joinToString("|")

    fun hash(payload: String, previousEventHash: String?): String {
        val md = MessageDigest.getInstance("SHA-256")
        val input = (previousEventHash.orEmpty() + payload).toByteArray(Charsets.UTF_8)
        return md.digest(input).joinToString("") { "%02x".format(it) }
    }

    fun validateChain(eventsOldestFirst: List<SecurityEvent>): Boolean {
        var prev: String? = null
        for (event in eventsOldestFirst) {
            val payload = canonicalPayload(
                id = event.id,
                occurredAtEpochMillis = event.occurredAtEpochMillis,
                category = event.category,
                severity = event.severity,
                title = event.title,
                sourceDetector = event.sourceDetector
            )
            val expected = hash(payload, prev)
            if (event.eventHash != expected) return false
            if (event.previousEventHash != prev) return false
            prev = event.eventHash
        }
        return true
    }
}

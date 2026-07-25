package com.coldboar.coreguard.quilla

/**
 * Thread-safe in-memory store for [QuillaHypothesis] records produced by
 * [QuillaCorrelationEngine].
 *
 * Duplicate ids are replaced on [upsert] so the store always holds the most
 * recent state for a given hypothesis id.
 */
class QuillaHypothesisStore {

    private val lock = Any()
    private val hypotheses = mutableMapOf<String, QuillaHypothesis>()

    /**
     * Inserts or replaces the hypothesis keyed by [QuillaHypothesis.id].
     */
    fun upsert(hypothesis: QuillaHypothesis) {
        synchronized(lock) {
            hypotheses[hypothesis.id] = hypothesis
        }
    }

    /** Returns a snapshot of all currently stored hypotheses. */
    fun all(): List<QuillaHypothesis> = synchronized(lock) { hypotheses.values.toList() }

    /** Removes all stored hypotheses. */
    fun clear() {
        synchronized(lock) { hypotheses.clear() }
    }
}

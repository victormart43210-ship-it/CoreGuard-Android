package com.coreguard.android.data.local.dao

import com.coreguard.android.data.local.entity.QuillaHypothesisEntity

/**
 * Data-access interface for persisting [QuillaHypothesisEntity] records.
 *
 * In production this is implemented by Room; in tests it is provided as a mock.
 */
interface QuillaLearningDao {
    /**
     * Inserts or replaces the given [hypothesis] in the persistent store.
     */
    fun upsertHypothesis(hypothesis: QuillaHypothesisEntity)
}

package com.coreguard.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.coreguard.android.data.local.entity.QuillaHypothesisEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data-access object for persisting [QuillaHypothesisEntity] records in the
 * [com.coreguard.android.data.local.QuillaDatabase] Room database.
 *
 * In production this is implemented by Room; in tests it is provided as a mock.
 */
@Dao
abstract class QuillaLearningDao {
    /**
     * Inserts or replaces the given [hypothesis] in the persistent store.
     * Room auto-generates the [QuillaHypothesisEntity.id] on insert when it is 0.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun upsertHypothesis(hypothesis: QuillaHypothesisEntity)

    /**
     * Returns a [Flow] that emits all stored hypotheses ordered from most recent to oldest,
     * and re-emits whenever the underlying table changes.
     */
    @Query("SELECT * FROM quilla_hypotheses ORDER BY id DESC")
    abstract fun getAllHypotheses(): Flow<List<QuillaHypothesisEntity>>

    /**
     * Removes all hypothesis records from the table.
     */
    @Query("DELETE FROM quilla_hypotheses")
    abstract fun clearAll()
}

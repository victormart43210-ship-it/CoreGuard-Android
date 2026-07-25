package com.coldboar.coreguard.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.coldboar.coreguard.data.local.entity.QuillaHypothesisEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class QuillaLearningDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertHypothesis(hypothesis: QuillaHypothesisEntity)

    @Query("SELECT * FROM quilla_hypotheses WHERE status = 'ACTIVE' ORDER BY timestamp DESC")
    abstract fun observeActiveHypotheses(): Flow<List<QuillaHypothesisEntity>>

    @Query("SELECT * FROM quilla_hypotheses ORDER BY timestamp DESC")
    abstract fun observeAllHypotheses(): Flow<List<QuillaHypothesisEntity>>

    @Query("UPDATE quilla_hypotheses SET status = :status WHERE id = :id")
    abstract suspend fun updateStatus(id: String, status: String)

    @Query("DELETE FROM quilla_hypotheses WHERE id = :id")
    abstract suspend fun deleteById(id: String)
}

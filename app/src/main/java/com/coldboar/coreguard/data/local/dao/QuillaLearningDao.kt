package com.coldboar.coreguard.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.coldboar.coreguard.data.local.entity.QuillaDeviceProfileEntity
import com.coldboar.coreguard.data.local.entity.QuillaHypothesisEntity
import com.coldboar.coreguard.data.local.entity.QuillaLearningJournalEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class QuillaLearningDao {

    // ── Device profile ────────────────────────────────────────────────────────

    @Query("SELECT * FROM quilla_device_profile WHERE id = 1 LIMIT 1")
    abstract fun getDeviceProfileFlow(): Flow<QuillaDeviceProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertDeviceProfile(profile: QuillaDeviceProfileEntity)

    // ── Learning journal ──────────────────────────────────────────────────────

    @Query("SELECT * FROM quilla_learning_journal ORDER BY timestamp DESC LIMIT :limit")
    abstract fun getRecentJournalEntries(limit: Int): Flow<List<QuillaLearningJournalEntity>>

    @Insert
    abstract suspend fun insertJournalEntry(entry: QuillaLearningJournalEntity)

    // ── Hypotheses ────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertHypothesis(hypothesis: QuillaHypothesisEntity)

    @Query("SELECT * FROM quilla_hypotheses WHERE status = 'ACTIVE' ORDER BY confidence DESC")
    abstract fun getActiveHypothesesFlow(): Flow<List<QuillaHypothesisEntity>>

    // ── Memory management ─────────────────────────────────────────────────────

    @Query("DELETE FROM quilla_learning_journal WHERE packageName = :packageName")
    abstract suspend fun forgetApp(packageName: String)

    @Query("DELETE FROM quilla_learning_journal")
    abstract suspend fun clearAllJournal()

    @Query("DELETE FROM quilla_hypotheses")
    abstract suspend fun clearAllHypotheses()

    @Query("DELETE FROM quilla_device_profile")
    abstract suspend fun clearDeviceProfile()

    /**
     * Atomically wipes all Quilla memory: journal, hypotheses, and device
     * profile. Wrapped in a single transaction to keep the database consistent.
     */
    @Transaction
    open suspend fun fullLearningReset() {
        clearAllJournal()
        clearAllHypotheses()
        clearDeviceProfile()
    }
}

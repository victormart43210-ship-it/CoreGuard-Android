package com.coldboar.coreguard.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * An auditable log entry recording why Quilla updated her understanding of a
 * package's behavior.
 */
@Entity(tableName = "quilla_learning_journal")
data class QuillaLearningJournalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val eventType: String,
    val summary: String,
    val timestamp: Long = System.currentTimeMillis(),
    val confidence: Float
)

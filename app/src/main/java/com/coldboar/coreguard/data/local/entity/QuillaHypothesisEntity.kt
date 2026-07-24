package com.coldboar.coreguard.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single correlated threat hypothesis produced by [com.coldboar.coreguard.domain.quilla.QuillaCorrelationEngine].
 * Hypotheses are ACTIVE until resolved or decayed.
 */
@Entity(tableName = "quilla_hypotheses")
data class QuillaHypothesisEntity(
    @PrimaryKey val id: String,
    val hypothesisType: String,
    val summary: String,
    val evidenceJson: String,
    val confidence: Float,
    val status: String,
    val createdAt: Long = System.currentTimeMillis()
)

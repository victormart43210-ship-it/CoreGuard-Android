package com.coldboar.coreguard.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quilla_hypotheses")
data class QuillaHypothesisEntity(
    @PrimaryKey val id: String,
    val hypothesisType: String,
    val summary: String,
    val evidenceJson: String,
    val confidence: Float,
    val status: String, // "ACTIVE", "DISMISSED", "RESOLVED"
    val timestamp: Long = System.currentTimeMillis()
)

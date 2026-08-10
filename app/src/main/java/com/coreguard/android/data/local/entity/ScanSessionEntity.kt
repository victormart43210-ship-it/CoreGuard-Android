package com.coreguard.android.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_sessions")
data class ScanSessionEntity(
    @PrimaryKey val sessionId: String,
    val status: String,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val failureReason: String?,
    val scannerEngineVersion: String,
    val schemaVersion: Int,
    val observedFindings: Int,
    val inferredFindings: Int,
    val unavailableFindings: Int,
    val deepInspectionEnabled: Boolean,
    val feedSource: String,
    val feedVersion: String?,
    val feedAuthenticity: String,
    val feedLoadedAtMs: Long
)


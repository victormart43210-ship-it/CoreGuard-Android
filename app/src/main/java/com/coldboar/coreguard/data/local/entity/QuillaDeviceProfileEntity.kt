package com.coldboar.coreguard.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Singleton row (id=1) that tracks the overall state of Quilla's learned
 * baseline for this device.
 */
@Entity(tableName = "quilla_device_profile")
data class QuillaDeviceProfileEntity(
    @PrimaryKey val id: Int = 1,
    /** 0–100 percentage representing how mature the baseline is. */
    val baselineMaturity: Int = 0,
    val totalScansAnalyzed: Long = 0L,
    val learningStartedAt: Long = System.currentTimeMillis(),
    val lastUpdatedAt: Long = System.currentTimeMillis()
)

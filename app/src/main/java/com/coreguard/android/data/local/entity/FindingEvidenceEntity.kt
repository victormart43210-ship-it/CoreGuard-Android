package com.coreguard.android.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "finding_evidence",
    foreignKeys = [
        ForeignKey(
            entity = FindingEntity::class,
            parentColumns = ["id"],
            childColumns = ["findingRowId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("findingRowId")]
)
data class FindingEvidenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val findingRowId: Long,
    val evidenceType: String,
    val evidenceValue: String,
    val independentSource: Boolean
)


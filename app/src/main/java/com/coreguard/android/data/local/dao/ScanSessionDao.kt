package com.coreguard.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.coreguard.android.data.local.entity.FindingEntity
import com.coreguard.android.data.local.entity.FindingEvidenceEntity
import com.coreguard.android.data.local.entity.ScanSessionEntity
import com.coreguard.android.data.local.entity.ScanStageEventEntity
import com.coreguard.android.data.local.entity.ThreatIntelReferenceEntity

@Dao
interface ScanSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSession(session: ScanSessionEntity)

    @Insert
    fun insertFindings(findings: List<FindingEntity>): List<Long>

    @Insert
    fun insertFindingEvidence(evidence: List<FindingEvidenceEntity>)

    @Insert
    fun insertStageEvents(events: List<ScanStageEventEntity>)

    @Insert
    fun insertThreatIntelReferences(references: List<ThreatIntelReferenceEntity>)

    @Query("SELECT COUNT(*) FROM scan_sessions")
    fun countSessions(): Int

    @Transaction
    fun insertSessionGraph(
        session: ScanSessionEntity,
        findings: List<FindingEntity>,
        evidence: List<FindingEvidenceEntity>,
        stages: List<ScanStageEventEntity>,
        references: List<ThreatIntelReferenceEntity>
    ) {
        insertSession(session)
        insertStageEvents(stages)
        if (findings.isNotEmpty()) {
            insertFindings(findings)
        }
        if (evidence.isNotEmpty()) {
            insertFindingEvidence(evidence)
        }
        if (references.isNotEmpty()) {
            insertThreatIntelReferences(references)
        }
    }
}


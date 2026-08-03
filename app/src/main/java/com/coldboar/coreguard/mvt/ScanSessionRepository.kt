package com.coldboar.coreguard.mvt

import android.content.Context
import com.coldboar.coreguard.truth.EvidenceClass
import com.coldboar.coreguard.truth.Finding
import com.coreguard.android.data.local.QuillaDatabase
import com.coreguard.android.data.local.entity.FindingEntity
import com.coreguard.android.data.local.entity.ScanSessionEntity
import com.coreguard.android.data.local.entity.ScanStageEventEntity
import java.util.UUID

data class ScanSessionSaveRequest(
    val status: ScanStageId,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val failureReason: String? = null,
    val scannerEngineVersion: String,
    val schemaVersion: Int,
    val deepInspectionEnabled: Boolean,
    val feedSource: String,
    val feedVersion: String?,
    val feedAuthenticity: String,
    val feedLoadedAtMs: Long,
    val findings: List<Finding>,
    val stageEvents: List<ScanStageEvent>
)

interface ScanSessionRepository {
    fun ensureLegacyImport()
    fun saveSession(request: ScanSessionSaveRequest): String
}

class RoomScanSessionRepository(
    private val context: Context,
    private val database: QuillaDatabase = QuillaDatabase.getInstance(context)
) : ScanSessionRepository {

    private val prefs by lazy {
        context.getSharedPreferences("coreguard_scan_history_import", Context.MODE_PRIVATE)
    }

    override fun ensureLegacyImport() {
        if (prefs.getBoolean("imported_v1", false)) return
        val dao = database.scanSessionDao()
        if (dao.countSessions() > 0) {
            prefs.edit().putBoolean("imported_v1", true).apply()
            return
        }
        val oldRecords = ScanHistoryStore.load(context)
        oldRecords.forEachIndexed { idx, record ->
            val id = "legacy-${record.timestampMs}-$idx"
            dao.insertSession(
                ScanSessionEntity(
                    sessionId = id,
                    status = ScanStageId.COMPLETED.name,
                    startedAtMs = record.timestampMs - record.durationMillis,
                    endedAtMs = record.timestampMs,
                    failureReason = null,
                    scannerEngineVersion = "legacy",
                    schemaVersion = 1,
                    observedFindings = record.detectionCount,
                    inferredFindings = 0,
                    unavailableFindings = 0,
                    deepInspectionEnabled = true,
                    feedSource = "Legacy shared preferences history",
                    feedVersion = null,
                    feedAuthenticity = "Unknown",
                    feedLoadedAtMs = 0L
                )
            )
        }
        prefs.edit().putBoolean("imported_v1", true).apply()
    }

    override fun saveSession(request: ScanSessionSaveRequest): String {
        val sessionId = UUID.randomUUID().toString()
        val observed = request.findings.count { it.evidenceClass == EvidenceClass.OBSERVED }
        val inferred = request.findings.count { it.evidenceClass == EvidenceClass.INFERRED }
        val unavailable = request.findings.count { it.evidenceClass == EvidenceClass.UNAVAILABLE }
        val dao = database.scanSessionDao()

        dao.insertSession(
            ScanSessionEntity(
                sessionId = sessionId,
                status = request.status.name,
                startedAtMs = request.startedAtMs,
                endedAtMs = request.endedAtMs,
                failureReason = request.failureReason,
                scannerEngineVersion = request.scannerEngineVersion,
                schemaVersion = request.schemaVersion,
                observedFindings = observed,
                inferredFindings = inferred,
                unavailableFindings = unavailable,
                deepInspectionEnabled = request.deepInspectionEnabled,
                feedSource = request.feedSource,
                feedVersion = request.feedVersion,
                feedAuthenticity = request.feedAuthenticity,
                feedLoadedAtMs = request.feedLoadedAtMs
            )
        )

        if (request.stageEvents.isNotEmpty()) {
            dao.insertStageEvents(
                request.stageEvents.map {
                    ScanStageEventEntity(
                        sessionId = sessionId,
                        stageId = it.stageId.name,
                        label = it.label,
                        completedUnits = it.completedUnits,
                        totalUnits = it.totalUnits,
                        timestampMs = it.timestampMs,
                        visibilityLimitation = it.visibilityLimitation
                    )
                }
            )
        }

        if (request.findings.isNotEmpty()) {
            dao.insertFindings(
                request.findings.map { finding ->
                    FindingEntity(
                        sessionId = sessionId,
                        findingId = finding.id,
                        title = finding.title,
                        severity = finding.severity.name,
                        confidence = finding.confidence.name,
                        evidenceClass = finding.evidenceClass.name,
                        affectedComponent = finding.affectedComponent,
                        source = finding.source,
                        verificationState = finding.verificationStatus
                    )
                }
            )
        }
        return sessionId
    }
}


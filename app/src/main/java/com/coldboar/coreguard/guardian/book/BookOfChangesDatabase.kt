package com.coldboar.coreguard.guardian.book

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.coldboar.coreguard.guardian.EvidenceClass
import com.coldboar.coreguard.guardian.EventHashChain
import com.coldboar.coreguard.guardian.FindingCategory
import com.coldboar.coreguard.guardian.SecurityEvent
import com.coldboar.coreguard.guardian.SecurityFinding
import com.coldboar.coreguard.guardian.Severity

@Entity(tableName = "security_events")
data class SecurityEventEntity(
    @PrimaryKey val id: String,
    val occurredAtEpochMillis: Long,
    val detectedAtEpochMillis: Long,
    val category: String,
    val severity: String,
    val evidenceClass: String,
    val title: String,
    val explanation: String,
    val relatedPackageName: String?,
    val evidenceIdsCsv: String,
    val sourceDetector: String,
    val eventHash: String,
    val previousEventHash: String?
)

@Dao
interface SecurityEventDao {
    @Query("SELECT * FROM security_events ORDER BY occurredAtEpochMillis DESC")
    fun allNewestFirst(): List<SecurityEventEntity>

    @Query("SELECT * FROM security_events ORDER BY occurredAtEpochMillis ASC")
    fun allOldestFirst(): List<SecurityEventEntity>

    @Query("SELECT * FROM security_events ORDER BY occurredAtEpochMillis DESC LIMIT 1")
    fun newest(): SecurityEventEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(entity: SecurityEventEntity): Long

    @Query("DELETE FROM security_events")
    fun clearAll()

    @Query(
        "DELETE FROM security_events WHERE occurredAtEpochMillis < :cutoff AND " +
            "id NOT IN (SELECT id FROM security_events ORDER BY occurredAtEpochMillis DESC LIMIT 1)"
    )
    fun deleteOlderThan(cutoff: Long)
}

@Database(entities = [SecurityEventEntity::class], version = 1, exportSchema = false)
abstract class BookOfChangesDatabase : RoomDatabase() {
    abstract fun securityEventDao(): SecurityEventDao

    companion object {
        @Volatile
        private var instance: BookOfChangesDatabase? = null

        fun get(context: Context): BookOfChangesDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    BookOfChangesDatabase::class.java,
                    "guardian_book_of_changes.db"
                )
                    .fallbackToDestructiveMigration()
                    .allowMainThreadQueries()
                    .build()
                    .also { instance = it }
            }
    }
}

/**
 * Book of Changes repository — meaningful security changes over time (Blueprint §9).
 * Hash chain is tamper-evident, not tamper-proof.
 */
class BookOfChangesRepository private constructor(
    private val dao: SecurityEventDao
) {

    fun eventsNewestFirst(): List<SecurityEvent> =
        dao.allNewestFirst().map { it.toDomain() }

    fun chainValid(): Boolean =
        EventHashChain.validateChain(dao.allOldestFirst().map { it.toDomain() })

    fun recordFinding(finding: SecurityFinding) {
        val newest = dao.newest()
        // Deduplicate: skip if same detector title recorded in the last minute.
        if (newest != null &&
            newest.sourceDetector == finding.id &&
            finding.lastSeenEpochMillis - newest.occurredAtEpochMillis < 60_000L
        ) {
            return
        }
        val prevHash = newest?.eventHash
        val id = "${finding.id}-${finding.lastSeenEpochMillis}"
        val payload = EventHashChain.canonicalPayload(
            id = id,
            occurredAtEpochMillis = finding.lastSeenEpochMillis,
            category = finding.category,
            severity = finding.severity,
            title = finding.title,
            sourceDetector = finding.id
        )
        val hash = EventHashChain.hash(payload, prevHash)
        dao.insert(
            SecurityEventEntity(
                id = id,
                occurredAtEpochMillis = finding.lastSeenEpochMillis,
                detectedAtEpochMillis = System.currentTimeMillis(),
                category = finding.category.name,
                severity = finding.severity.name,
                evidenceClass = finding.primaryEvidenceClass.name,
                title = finding.title,
                explanation = finding.plainLanguageSummary,
                relatedPackageName = null,
                evidenceIdsCsv = finding.evidence.joinToString(",") { it.id },
                sourceDetector = finding.id,
                eventHash = hash,
                previousEventHash = prevHash
            )
        )
    }

    fun clearAll() = dao.clearAll()

    fun applyRetention(days: Int) {
        if (days <= 0) return
        val cutoff = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000
        dao.deleteOlderThan(cutoff)
    }

    private fun SecurityEventEntity.toDomain(): SecurityEvent =
        SecurityEvent(
            id = id,
            occurredAtEpochMillis = occurredAtEpochMillis,
            detectedAtEpochMillis = detectedAtEpochMillis,
            category = FindingCategory.valueOf(category),
            severity = Severity.valueOf(severity),
            evidenceClass = EvidenceClass.valueOf(evidenceClass),
            title = title,
            explanation = explanation,
            relatedPackageName = relatedPackageName,
            evidenceIds = evidenceIdsCsv.split(',').filter { it.isNotBlank() },
            sourceDetector = sourceDetector,
            eventHash = eventHash,
            previousEventHash = previousEventHash
        )

    companion object {
        @Volatile
        private var instance: BookOfChangesRepository? = null

        fun get(context: Context): BookOfChangesRepository =
            instance ?: synchronized(this) {
                instance ?: BookOfChangesRepository(
                    BookOfChangesDatabase.get(context).securityEventDao()
                ).also { instance = it }
            }
    }
}

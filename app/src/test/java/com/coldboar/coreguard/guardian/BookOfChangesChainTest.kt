package com.coldboar.coreguard.guardian

import com.coldboar.coreguard.guardian.book.BookOfChangesRepository
import com.coldboar.coreguard.guardian.book.SecurityEventDao
import com.coldboar.coreguard.guardian.book.SecurityEventEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for Book of Changes hash-chain integrity.
 *
 * Events are linked in append order, while their observation timestamps can
 * tie or move backwards during one intelligence refresh.
 */
class BookOfChangesChainTest {

    private class AppendOrderDao : SecurityEventDao {
        val rows = mutableListOf<SecurityEventEntity>()

        override fun allNewestFirst(): List<SecurityEventEntity> =
            rows.sortedByDescending { it.occurredAtEpochMillis }

        override fun allOldestFirst(): List<SecurityEventEntity> = rows.toList()

        override fun newest(): SecurityEventEntity? = rows.lastOrNull()

        override fun insert(entity: SecurityEventEntity): Long {
            if (rows.none { it.id == entity.id }) rows += entity
            return rows.size.toLong()
        }

        override fun clearAll() = rows.clear()

        override fun deleteOlderThan(cutoff: Long) {
            val head = rows.lastOrNull()
            rows.retainAll { it.occurredAtEpochMillis >= cutoff || it.id == head?.id }
        }
    }

    private class TimestampOrderDao : SecurityEventDao {
        val rows = mutableListOf<SecurityEventEntity>()

        override fun allNewestFirst(): List<SecurityEventEntity> =
            rows.sortedByDescending { it.occurredAtEpochMillis }

        override fun allOldestFirst(): List<SecurityEventEntity> =
            rows.sortedBy { it.occurredAtEpochMillis }

        override fun newest(): SecurityEventEntity? =
            rows.maxByOrNull { it.occurredAtEpochMillis }

        override fun insert(entity: SecurityEventEntity): Long {
            if (rows.none { it.id == entity.id }) rows += entity
            return rows.size.toLong()
        }

        override fun clearAll() = rows.clear()

        override fun deleteOlderThan(cutoff: Long) = Unit
    }

    private fun finding(id: String, lastSeen: Long): SecurityFinding =
        SecurityFinding(
            id = id,
            category = FindingCategory.DEVICE_INTEGRITY,
            severity = Severity.REVIEW_SUGGESTED,
            confidence = Confidence.MEDIUM,
            title = "Finding $id",
            plainLanguageSummary = "summary",
            whyItMatters = "why",
            possibleBenignCauses = emptyList(),
            evidence = listOf(
                Evidence(
                    id = "$id-e",
                    evidenceClass = EvidenceClass.OBSERVED,
                    source = "test",
                    summary = "observed",
                    collectedAtEpochMillis = lastSeen
                )
            ),
            recommendedActions = emptyList(),
            firstSeenEpochMillis = lastSeen,
            lastSeenEpochMillis = lastSeen,
            active = true,
            detectorVersion = "1"
        )

    @Test
    fun chainStaysValidWhenFindingsShareATimestamp() {
        val dao = AppendOrderDao()
        val repo = BookOfChangesRepository(dao)
        val sameInstant = 1_000_000L
        repo.recordFinding(finding("check.a", sameInstant))
        repo.recordFinding(finding("check.b", sameInstant))
        repo.recordFinding(finding("check.c", sameInstant))

        assertTrue("Ledger must validate when timestamps tie", repo.chainValid())
    }

    @Test
    fun chainStaysValidWhenTimestampsGoBackwards() {
        val dao = AppendOrderDao()
        val repo = BookOfChangesRepository(dao)
        repo.recordFinding(finding("check.a", 5_000L))
        repo.recordFinding(finding("check.b", 1_000L))
        repo.recordFinding(finding("check.c", 3_000L))

        assertTrue("Ledger must validate when event times are not monotonic", repo.chainValid())
    }

    @Test
    fun timestampOrderedChainReproducesTheOriginalFailure() {
        val dao = TimestampOrderDao()
        val repo = BookOfChangesRepository(dao)
        repo.recordFinding(finding("check.a", 5_000L))
        repo.recordFinding(finding("check.b", 1_000L))

        assertFalse("Timestamp ordering is the defect under test", repo.chainValid())
    }
}

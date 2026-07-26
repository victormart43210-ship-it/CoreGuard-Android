package com.coldboar.coreguard

import android.content.Context
import com.coldboar.coreguard.elite.EliteModule
import com.coldboar.coreguard.elite.ScamGuardEngine
import com.coldboar.coreguard.mvt.IocRepository
import com.coldboar.coreguard.mvt.ScanHistoryStore
import java.io.File

/**
 * User-facing retention / deletion for on-device security artifacts.
 *
 * Does not delete Google Play purchase state (owned by Play). Does not revoke
 * OS permissions (VPN / Notification access) — those are system settings.
 *
 * Call [wipeAll] off the main thread — Room forbids [clearAllTables] on UI thread.
 */
object LocalSecurityData {

    data class WipeResult(
        val scanHistory: Boolean,
        val forensicJournal: Boolean,
        val scamGuard: Boolean,
        val threatCounter: Boolean,
        val downloadedIocs: Boolean,
        val quillaDb: Boolean
    ) {
        val allOk: Boolean
            get() = scanHistory && forensicJournal && scamGuard && threatCounter &&
                downloadedIocs && quillaDb

        fun summary(): String = if (allOk) {
            "Local security data deleted"
        } else {
            buildString {
                append("Deleted with issues:")
                if (!scanHistory) append(" scan history")
                if (!forensicJournal) append(" journal")
                if (!scamGuard) append(" scam findings")
                if (!threatCounter) append(" threat counter")
                if (!downloadedIocs) append(" IOC feeds")
                if (!quillaDb) append(" Quilla DB")
            }.trim()
        }
    }

    /**
     * Clears scan history, forensic journal, in-memory Scam Guard findings,
     * Elite threat Counter, downloaded user IOC feeds, and Quilla hypothesis DB.
     *
     * Must not be called on the main thread (Room [clearAllTables] requirement).
     */
    fun wipeAll(context: Context): WipeResult {
        val scanOk = runCatching { ScanHistoryStore.clear(context) }.isSuccess
        val journalOk = runCatching { EliteModule.clearJournal(context) }.isSuccess
        val counterOk = runCatching { EliteModule.resetThreatCounter() }.isSuccess
        val scamOk = runCatching { ScamGuardEngine.clear() }.isSuccess
        val iocOk = runCatching { clearDownloadedIocs(context) }.isSuccess
        val quillaOk = runCatching {
            val db = CoreGuardApplication.get()?.quillaDatabase
            if (db != null) {
                db.clearAllTables()
            }
            // If Application is null (tests), treat as OK — nothing to wipe.
            true
        }.getOrDefault(false)
        IocRepository.invalidate()
        return WipeResult(
            scanHistory = scanOk,
            forensicJournal = journalOk,
            scamGuard = scamOk,
            threatCounter = counterOk,
            downloadedIocs = iocOk,
            quillaDb = quillaOk
        )
    }

    /** Removes user-imported / downloaded IOC JSON under filesDir/ioc only. */
    fun clearDownloadedIocs(context: Context) {
        val filesDir = context.filesDir ?: return
        val dir = File(filesDir, "ioc")
        if (dir.isDirectory) {
            dir.listFiles()?.forEach { it.delete() }
        }
        IocRepository.invalidate()
    }
}

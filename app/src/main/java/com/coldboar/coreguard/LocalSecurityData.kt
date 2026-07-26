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
 */
object LocalSecurityData {

    /**
     * Clears scan history, forensic journal, in-memory Scam Guard findings,
     * Elite threat Counter, downloaded user IOC feeds, and Quilla hypothesis DB.
     */
    fun wipeAll(context: Context) {
        runCatching { ScanHistoryStore.clear(context) }
        runCatching { EliteModule.clearJournal(context) }
        EliteModule.resetThreatCounter()
        ScamGuardEngine.clear()
        runCatching { clearDownloadedIocs(context) }
        runCatching {
            CoreGuardApplication.get()?.quillaDatabase?.clearAllTables()
        }
        IocRepository.invalidate()
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

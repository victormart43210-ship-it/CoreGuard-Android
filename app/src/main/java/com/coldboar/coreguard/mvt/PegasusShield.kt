package com.coldboar.coreguard.mvt

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** Convenience controller for starting/stopping the [GuardVpnService]. */
object PegasusShield {

    fun start(context: Context) {
        ContextCompat.startForegroundService(context, Intent(context, GuardVpnService::class.java))
    }

    fun stop(context: Context) {
        val intent = Intent(context, GuardVpnService::class.java).setAction(GuardVpnService.ACTION_STOP)
        ContextCompat.startForegroundService(context, intent)
    }
}

/**
 * Holds the most recent [ScanReport] so other surfaces (e.g. the Security
 * Dashboard) can reflect the latest forensic verdict without re-scanning.
 */
object LastScan {
    @Volatile
    var report: ScanReport? = null
}

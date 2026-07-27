package com.coldboar.coreguard.monitor

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.coldboar.coreguard.GuardianScore
import com.coldboar.coreguard.SecurityCheckRunner
import java.util.concurrent.TimeUnit

/**
 * Deferrable, battery-aware Guardian Score pulse.
 *
 * Runs only when the battery is not low and the device is not in an extreme
 * power-save posture (WorkManager constraints). Does **not** require network.
 * Cadence is hourly — not continuous foreground polling.
 */
class SecurityPulseWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val results = SecurityCheckRunner.run(applicationContext)
            val score = GuardianScore.compute(results)
            val rank = GuardianScore.rankFor(score).userLabel
            SecurityScoreCache.write(applicationContext, score, rank)
            Log.i(TAG, "Security pulse cached score=$score")
            Result.success()
        } catch (t: Throwable) {
            Log.w(TAG, "Security pulse failed: ${t.message}")
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "SecurityPulse"
        const val UNIQUE_WORK = "coreguard_security_pulse"
        /** Hourly is enough for background heuristic refresh; UI still refreshes live while open. */
        const val INTERVAL_HOURS = 1L

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()
            val request = PeriodicWorkRequestBuilder<SecurityPulseWorker>(
                INTERVAL_HOURS,
                TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}

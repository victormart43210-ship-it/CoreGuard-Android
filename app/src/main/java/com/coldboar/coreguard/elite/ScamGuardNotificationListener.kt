package com.coldboar.coreguard.elite

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Optional [NotificationListenerService] for Scam Guard.
 *
 * User must explicitly enable CoreGuard under Notification access settings.
 * We only parse notification text locally for URLs — no cloud upload.
 */
class ScamGuardNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val text = extras.getCharSequence("android.text")?.toString().orEmpty()
        val big = extras.getCharSequence("android.bigText")?.toString().orEmpty()
        val blob = listOf(title, text, big).filter { it.isNotBlank() }.joinToString("\n")
        if (blob.isBlank()) return
        runCatching {
            ScamGuardEngine.inspectNotificationText(
                applicationContext,
                blob,
                source = sbn.packageName ?: "notification"
            )
        }.onFailure {
            Log.w(TAG, "Scam Guard inspect failed: ${it.message}")
        }
    }

    companion object {
        private const val TAG = "ScamGuardListener"
    }
}

package com.coldboar.coreguard.elite

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Optional [NotificationListenerService] for Scam Guard.
 *
 * ## Privilege + privacy
 *
 * The user must explicitly enable CoreGuard under **Notification access**.
 * We only parse notification text locally for URLs — no cloud upload, no SMS
 * inbox permission.
 *
 * ## Module pattern
 *
 * This service must not talk to Compose. It calls [EliteModule.inspectScamText],
 * which updates Scam Guard memory, the Forensic Journal (amber+), and the
 * Redux [EliteThreatCounterStore] so the Home amber pill can recompose.
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
            // Façade entry — keeps Counter / journal wiring out of this Service.
            EliteModule.inspectScamText(
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

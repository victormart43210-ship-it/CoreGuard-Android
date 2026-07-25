package com.coldboar.coreguard.hardening

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast

/**
 * Opens the closest system Settings screen for a [DeviceHardeningGuide.SettingsDeepLink].
 * Falls back gracefully when a manufacturer omits the exact action.
 */
object HardeningSettingsIntents {

    fun open(context: Context, link: DeviceHardeningGuide.SettingsDeepLink) {
        val intent = intentFor(link) ?: run {
            if (link == DeviceHardeningGuide.SettingsDeepLink.DEVELOPER_OPTIONS_HINT) {
                Toast.makeText(
                    context,
                    "Open Settings → About phone → tap Build number 7×, then open Developer options.",
                    Toast.LENGTH_LONG
                ).show()
            }
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            // Last-resort fallback to the top-level Settings app.
            try {
                context.startActivity(
                    Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
                Toast.makeText(context, "Unable to open system Settings on this device.", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    internal fun intentFor(link: DeviceHardeningGuide.SettingsDeepLink): Intent? = when (link) {
        DeviceHardeningGuide.SettingsDeepLink.NONE -> null
        DeviceHardeningGuide.SettingsDeepLink.APPS -> Intent(Settings.ACTION_APPLICATION_SETTINGS)
        DeviceHardeningGuide.SettingsDeepLink.BATTERY_OPTIMIZATION ->
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        DeviceHardeningGuide.SettingsDeepLink.STORAGE -> Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
        DeviceHardeningGuide.SettingsDeepLink.SECURITY -> Intent(Settings.ACTION_SECURITY_SETTINGS)
        DeviceHardeningGuide.SettingsDeepLink.DEVELOPER_OPTIONS_HINT ->
            Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)
    }
}

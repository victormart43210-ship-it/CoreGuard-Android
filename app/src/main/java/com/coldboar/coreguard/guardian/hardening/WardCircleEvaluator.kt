package com.coldboar.coreguard.guardian.hardening

import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.coldboar.coreguard.guardian.ActionType
import com.coldboar.coreguard.guardian.EvidenceClass
import com.coldboar.coreguard.guardian.HardeningCheck
import com.coldboar.coreguard.guardian.HardeningStatus
import com.coldboar.coreguard.guardian.RecommendedAction
import com.coldboar.coreguard.guardian.Severity
import com.coldboar.coreguard.mvt.ShieldState

/**
 * Ward Circle hardening journey checks (Blueprint §12).
 * Completion never implies the device is unhackable.
 */
object WardCircleEvaluator {

    fun evaluate(context: Context): List<HardeningCheck> {
        val now = System.currentTimeMillis()
        return listOf(
            screenLock(context, now),
            developerOptions(context, now),
            unknownSources(context, now),
            accessibility(context, now),
            deviceAdmin(context, now),
            vpnOptIn(now),
            privateDns(context, now),
            securityPatch(now),
            notificationListenerManual(now)
        )
    }

    fun completionPercent(checks: List<HardeningCheck>): Int {
        if (checks.isEmpty()) return 0
        // Manual checks do not advance the ring — only observed PASSED does.
        val passed = checks.count { it.status == HardeningStatus.PASSED }
        return ((passed * 100f) / checks.size).toInt().coerceIn(0, 100)
    }

    private fun screenLock(context: Context, now: Long): HardeningCheck {
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val secure = km.isDeviceSecure
        return HardeningCheck(
            id = "ward.screen_lock",
            title = "Screen lock",
            description = if (secure) {
                "A secure lock screen is configured (observed via KeyguardManager)."
            } else {
                "No secure lock screen detected. Add PIN, pattern, or biometric."
            },
            status = if (secure) HardeningStatus.PASSED else HardeningStatus.FAILED,
            evidenceClass = EvidenceClass.OBSERVED,
            importance = Severity.ELEVATED_CONCERN,
            action = settingsAction("Open security settings", "android.settings.SECURITY_SETTINGS"),
            lastCheckedEpochMillis = now
        )
    }

    private fun developerOptions(context: Context, now: Long): HardeningCheck {
        val enabled = Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
            0
        ) == 1
        return HardeningCheck(
            id = "ward.developer_options",
            title = "Developer options",
            description = if (enabled) {
                "Developer options appear enabled — review if you are not actively developing."
            } else {
                "Developer options are off."
            },
            status = if (enabled) HardeningStatus.REVIEW else HardeningStatus.PASSED,
            evidenceClass = EvidenceClass.OBSERVED,
            importance = Severity.REVIEW_SUGGESTED,
            action = settingsAction("Developer settings", "android.settings.APPLICATION_DEVELOPMENT_SETTINGS"),
            lastCheckedEpochMillis = now
        )
    }

    private fun unknownSources(context: Context, now: Long): HardeningCheck {
        // canRequestPackageInstalls() throws SecurityException unless the caller
        // declares REQUEST_INSTALL_PACKAGES. CoreGuard deliberately does not request
        // that sensitive install permission, so an unreadable value is reported as
        // UNAVAILABLE — never assumed safe.
        val allowed: Boolean? = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.packageManager.canRequestPackageInstalls()
            } else {
                @Suppress("DEPRECATION")
                Settings.Secure.getInt(context.contentResolver, Settings.Secure.INSTALL_NON_MARKET_APPS, 0) == 1
            }
        }.getOrNull()

        if (allowed == null) {
            return HardeningCheck(
                id = "ward.unknown_sources",
                title = "Install unknown apps",
                description = "Android did not allow CoreGuard to read this setting. " +
                    "Reported as unknown rather than safe.",
                status = HardeningStatus.UNAVAILABLE,
                evidenceClass = EvidenceClass.UNAVAILABLE,
                importance = Severity.REVIEW_SUGGESTED,
                action = settingsAction("App install permissions", Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES),
                lastCheckedEpochMillis = now
            )
        }

        return HardeningCheck(
            id = "ward.unknown_sources",
            title = "Install unknown apps",
            description = if (allowed) {
                "This app can request unknown-app installs — tighten if not needed."
            } else {
                "Unknown-app install permission for CoreGuard is not granted."
            },
            status = if (allowed) HardeningStatus.REVIEW else HardeningStatus.PASSED,
            evidenceClass = EvidenceClass.OBSERVED,
            importance = Severity.REVIEW_SUGGESTED,
            action = settingsAction("App install permissions", Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES),
            lastCheckedEpochMillis = now
        )
    }

    private fun accessibility(context: Context, now: Long): HardeningCheck {
        val raw = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        val count = raw.split(':').count { it.isNotBlank() }
        return HardeningCheck(
            id = "ward.accessibility",
            title = "Accessibility services",
            description = "Enabled accessibility services: $count. Review any you do not recognize.",
            status = when {
                count == 0 -> HardeningStatus.PASSED
                count <= 2 -> HardeningStatus.REVIEW
                else -> HardeningStatus.FAILED
            },
            evidenceClass = EvidenceClass.OBSERVED,
            importance = Severity.REVIEW_SUGGESTED,
            action = settingsAction("Accessibility settings", Settings.ACTION_ACCESSIBILITY_SETTINGS),
            lastCheckedEpochMillis = now
        )
    }

    private fun deviceAdmin(context: Context, now: Long): HardeningCheck {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val active = dpm.activeAdmins?.size ?: 0
        return HardeningCheck(
            id = "ward.device_admin",
            title = "Device administrators",
            description = "Active device admin apps: $active.",
            status = if (active == 0) HardeningStatus.PASSED else HardeningStatus.REVIEW,
            evidenceClass = EvidenceClass.OBSERVED,
            importance = Severity.REVIEW_SUGGESTED,
            action = settingsAction("Device admin apps", Settings.ACTION_SECURITY_SETTINGS),
            lastCheckedEpochMillis = now
        )
    }

    private fun vpnOptIn(now: Long): HardeningCheck {
        val on = ShieldState.isActive
        return HardeningCheck(
            id = "ward.vpn_shield",
            title = "Privacy Shield (VPN)",
            description = if (on) {
                "Privacy Shield VPN is active (user opt-in)."
            } else {
                "Privacy Shield is off — optional DNS filtering, never forced."
            },
            status = HardeningStatus.REVIEW,
            evidenceClass = EvidenceClass.OBSERVED,
            importance = Severity.INFORMATIONAL,
            action = RecommendedAction(
                id = "ward.open_shield",
                label = "Open Shield",
                explanation = "VPN remains user opt-in via Android consent.",
                actionType = ActionType.OPEN_APP_DETAILS,
                destination = "shield",
                requiresConfirmation = false
            ),
            lastCheckedEpochMillis = now
        )
    }

    private fun privateDns(context: Context, now: Long): HardeningCheck {
        val mode = runCatching {
            Settings.Global.getString(context.contentResolver, "private_dns_mode")
        }.getOrNull()
        val status = when (mode) {
            "hostname", "opportunistic" -> HardeningStatus.PASSED
            null -> HardeningStatus.UNAVAILABLE
            else -> HardeningStatus.REVIEW
        }
        return HardeningCheck(
            id = "ward.private_dns",
            title = "Private DNS",
            description = "Private DNS mode: ${mode ?: "unavailable on this device/API"}.",
            status = status,
            evidenceClass = if (mode == null) EvidenceClass.UNAVAILABLE else EvidenceClass.OBSERVED,
            importance = Severity.INFORMATIONAL,
            action = settingsAction("Network settings", Settings.ACTION_WIRELESS_SETTINGS),
            lastCheckedEpochMillis = now
        )
    }

    private fun securityPatch(now: Long): HardeningCheck {
        val patch = Build.VERSION.SECURITY_PATCH
        return HardeningCheck(
            id = "ward.security_patch",
            title = "Security patch level",
            description = "Reported patch level: $patch. Keep the OS updated when your OEM provides updates.",
            status = HardeningStatus.REVIEW,
            evidenceClass = EvidenceClass.OBSERVED,
            importance = Severity.INFORMATIONAL,
            action = settingsAction("System update", Settings.ACTION_SETTINGS),
            lastCheckedEpochMillis = now
        )
    }

    private fun notificationListenerManual(now: Long): HardeningCheck =
        HardeningCheck(
            id = "ward.notification_listener",
            title = "Notification access (Scam Guard)",
            description = "Notification Listener is optional and user-controlled. Confirm in system settings if you use Scam Guard.",
            status = HardeningStatus.MANUAL_CONFIRMATION_REQUIRED,
            evidenceClass = EvidenceClass.USER_REPORTED,
            importance = Severity.INFORMATIONAL,
            action = settingsAction(
                "Notification access",
                Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
            ),
            lastCheckedEpochMillis = now
        )

    private fun settingsAction(label: String, action: String) = RecommendedAction(
        id = "ward-$label",
        label = label,
        explanation = "Opens Android settings. CoreGuard does not change settings for you.",
        actionType = ActionType.OPEN_ANDROID_SETTINGS,
        destination = action,
        destructive = false,
        requiresConfirmation = true
    )
}

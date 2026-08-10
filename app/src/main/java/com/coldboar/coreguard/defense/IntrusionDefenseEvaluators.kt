package com.coldboar.coreguard.defense

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.coldboar.coreguard.SecurityCheckEvaluator
import com.coldboar.coreguard.SecurityCheckResult
import com.coldboar.coreguard.SecurityCheckState

/**
 * Defensive checks against intrusive Trojan / overlay / red-team style abuse.
 * Evidence-only — no supernatural claims. Injectable for unit tests.
 */

/**
 * Counts third-party apps holding [android.Manifest.permission.SYSTEM_ALERT_WINDOW].
 * Overlay phishing / banking Trojans often abuse draw-over-apps.
 */
class OverlayAbuseEvaluator(
    private val overlayAppCount: () -> Int,
    private val sampleLabels: () -> List<String> = { emptyList() }
) : SecurityCheckEvaluator {

    constructor(context: Context) : this(
        overlayAppCount = { countOverlayApps(context).first },
        sampleLabels = { countOverlayApps(context).second }
    )

    override fun evaluate(): SecurityCheckResult {
        val count = overlayAppCount()
        val samples = sampleLabels().take(3).joinToString(", ")
        return when {
            count <= 0 -> SecurityCheckResult(
                id = "overlay_abuse",
                displayName = "Overlay Surface",
                state = SecurityCheckState.PASS,
                explanation = "No third-party draw-over-apps holders found. Overlay phishing surface looks quiet."
            )
            count <= 2 -> SecurityCheckResult(
                id = "overlay_abuse",
                displayName = "Overlay Surface",
                state = SecurityCheckState.WARN,
                explanation = "$count app(s) can draw overlays" +
                    (if (samples.isNotBlank()) " ($samples)" else "") +
                    ". Review them — Trojans use overlays for fake login screens."
            )
            else -> SecurityCheckResult(
                id = "overlay_abuse",
                displayName = "Overlay Surface",
                state = SecurityCheckState.FAIL,
                explanation = "$count apps can draw overlays" +
                    (if (samples.isNotBlank()) " ($samples)" else "") +
                    ". High overlay surface — audit for banking/Trojan abuse."
            )
        }
    }

    companion object {
        fun countOverlayApps(context: Context): Pair<Int, List<String>> {
            val pm = context.packageName
            return runCatching {
                val pkgs = pmPackages(context)
                val holders = mutableListOf<String>()
                for (pkg in pkgs) {
                    if (pkg == pm) continue
                    if (hasOverlayPermission(context, pkg)) {
                        holders += pkg
                    }
                }
                holders.size to holders.take(5)
            }.getOrDefault(0 to emptyList())
        }

        private fun pmPackages(context: Context): List<String> {
            val flags = PackageManager.GET_PERMISSIONS
            @Suppress("DEPRECATION")
            val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getInstalledPackages(
                    PackageManager.PackageInfoFlags.of(flags.toLong())
                )
            } else {
                context.packageManager.getInstalledPackages(flags)
            }
            return packages.mapNotNull { it.packageName }
        }

        private fun hasOverlayPermission(context: Context, packageName: String): Boolean {
            return try {
                val uid = context.packageManager.getApplicationInfo(packageName, 0).uid
                val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
                val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    appOps.unsafeCheckOpNoThrow(
                        AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
                        uid,
                        packageName
                    )
                } else {
                    @Suppress("DEPRECATION")
                    appOps.checkOpNoThrow(
                        AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
                        uid,
                        packageName
                    )
                }
                mode == AppOpsManager.MODE_ALLOWED
            } catch (_: Exception) {
                false
            }
        }
    }
}

/**
 * Flags enabled accessibility services from non-system packages.
 * Malicious Trojans and stalkerware often abuse Accessibility for capture/control.
 */
class AccessibilityAbuseEvaluator(
    private val thirdPartyServiceCount: () -> Int,
    private val sampleLabels: () -> List<String> = { emptyList() }
) : SecurityCheckEvaluator {

    constructor(context: Context) : this(
        thirdPartyServiceCount = { enabledThirdPartyServices(context).first },
        sampleLabels = { enabledThirdPartyServices(context).second }
    )

    override fun evaluate(): SecurityCheckResult {
        val count = thirdPartyServiceCount()
        val samples = sampleLabels().take(3).joinToString(", ")
        return when {
            count <= 0 -> SecurityCheckResult(
                id = "accessibility_abuse",
                displayName = "Accessibility Surface",
                state = SecurityCheckState.PASS,
                explanation = "No third-party Accessibility services enabled. Keystroke/UI capture surface looks quiet."
            )
            count == 1 -> SecurityCheckResult(
                id = "accessibility_abuse",
                displayName = "Accessibility Surface",
                state = SecurityCheckState.WARN,
                explanation = "1 third-party Accessibility service enabled" +
                    (if (samples.isNotBlank()) " ($samples)" else "") +
                    ". Confirm you trust it — Trojans abuse this API."
            )
            else -> SecurityCheckResult(
                id = "accessibility_abuse",
                displayName = "Accessibility Surface",
                state = SecurityCheckState.FAIL,
                explanation = "$count third-party Accessibility services enabled" +
                    (if (samples.isNotBlank()) " ($samples)" else "") +
                    ". Elevated risk of intrusive capture / red-team tooling."
            )
        }
    }

    companion object {
        fun enabledThirdPartyServices(context: Context): Pair<Int, List<String>> {
            return runCatching {
                val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
                val enabled = am.getEnabledAccessibilityServiceList(
                    AccessibilityServiceInfo.FEEDBACK_ALL_MASK
                )
                val thirdParty = enabled.mapNotNull { info ->
                    val pkg = info.resolveInfo?.serviceInfo?.packageName ?: return@mapNotNull null
                    val appInfo = runCatching {
                        context.packageManager.getApplicationInfo(pkg, 0)
                    }.getOrNull() ?: return@mapNotNull pkg
                    val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    if (isSystem) null else pkg
                }.distinct()
                thirdParty.size to thirdParty.take(5)
            }.getOrDefault(0 to emptyList())
        }
    }
}

/**
 * Sideload / unknown-source install risk for this app and install-unknown-apps capability.
 * Unauthorized APK droppers often arrive outside Play.
 */
class SideloadRiskEvaluator(
    private val installedFromStore: () -> Boolean,
    private val canInstallUnknown: () -> Boolean = { false },
    private val installerLabel: () -> String = { "unknown" }
) : SecurityCheckEvaluator {

    constructor(context: Context) : this(
        installedFromStore = { isPlayOrSystemInstaller(context) },
        canInstallUnknown = {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.packageManager.canRequestPackageInstalls()
                } else {
                    @Suppress("DEPRECATION")
                    Settings.Secure.getInt(
                        context.contentResolver,
                        Settings.Secure.INSTALL_NON_MARKET_APPS,
                        0
                    ) == 1
                }
            } catch (e: SecurityException) {
                // Permission not available in test environments
                false
            }
        },
        installerLabel = { installerPackage(context) }
    )

    override fun evaluate(): SecurityCheckResult {
        val fromStore = installedFromStore()
        val unknownOk = canInstallUnknown()
        val installer = installerLabel()
        return when {
            fromStore && !unknownOk -> SecurityCheckResult(
                id = "sideload_risk",
                displayName = "Sideload Surface",
                state = SecurityCheckState.PASS,
                explanation = "CoreGuard installed via trusted installer ($installer); unknown-app installs not enabled for this app."
            )
            !fromStore && unknownOk -> SecurityCheckResult(
                id = "sideload_risk",
                displayName = "Sideload Surface",
                state = SecurityCheckState.FAIL,
                explanation = "Non-store installer ($installer) and unknown-app installs enabled — classic dropper/Trojan delivery path."
            )
            !fromStore || unknownOk -> SecurityCheckResult(
                id = "sideload_risk",
                displayName = "Sideload Surface",
                state = SecurityCheckState.WARN,
                explanation = "Sideload risk factors present (installer=$installer, unknownApps=$unknownOk). Prefer Play-sourced apps."
            )
            else -> SecurityCheckResult(
                id = "sideload_risk",
                displayName = "Sideload Surface",
                state = SecurityCheckState.PASS,
                explanation = "Sideload surface looks constrained."
            )
        }
    }

    companion object {
        fun installerPackage(context: Context): String {
            return runCatching {
                val pm = context.packageManager
                val pkg = context.packageName
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    pm.getInstallSourceInfo(pkg).installingPackageName
                        ?: pm.getInstallSourceInfo(pkg).initiatingPackageName
                        ?: "unknown"
                } else {
                    @Suppress("DEPRECATION")
                    pm.getInstallerPackageName(pkg) ?: "unknown"
                }
            }.getOrDefault("unknown")
        }

        fun isPlayOrSystemInstaller(context: Context): Boolean {
            val installer = installerPackage(context).lowercase()
            return installer.contains("com.android.vending") ||
                installer.contains("com.google.android.feedback") ||
                installer == "com.android.vending"
        }
    }
}

package com.coldboar.coreguard

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.security.MessageDigest

/**
 * Builds and runs the standard set of security check evaluators.
 *
 * Shared by [MainActivity] (Guardian Score preview) and
 * [SecurityDashboardActivity] (full per-check breakdown) so both screens are
 * always driven by the same evaluator configuration.
 */
object SecurityCheckRunner {

    fun run(context: Context): List<SecurityCheckResult> =
        evaluators(context).map { it.evaluate() }

    fun evaluators(context: Context): List<SecurityCheckEvaluator> = listOf(
        DebuggerCheckEvaluator(),
        EmulatorCheckEvaluator(),
        RootCheckEvaluator(),
        BuildTypeCheckEvaluator(),
        SignatureCheckEvaluator(actualSha256 = { certSha256(context) }),
        SpywareScanEvaluator()
    )

    /**
     * Returns the SHA-256 fingerprint of the first signing certificate, or
     * empty string on failure.
     */
    fun certSha256(context: Context): String {
        return try {
            val packageManager = context.packageManager
            val packageName = context.packageName
            @Suppress("DEPRECATION")
            val sig: Signature = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                    .signingInfo?.apkContentsSigners?.firstOrNull() ?: return ""
            } else {
                packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                    .signatures?.firstOrNull() ?: return ""
            }
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(sig.toByteArray())
            digest.joinToString(":") { "%02X".format(it) }
        } catch (_: Exception) {
            ""
        }
    }
}

package com.coldboar.coreguard

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import java.security.MessageDigest

/**
 * Runs the standard local security evaluator set and returns both results and
 * a [RestrictedModeDecision]. Prototype heuristics only.
 */
object SecuritySnapshot {

    fun capture(context: Context): Pair<List<SecurityCheckResult>, RestrictedModeDecision> {
        val evaluators: List<SecurityCheckEvaluator> = listOf(
            DebuggerCheckEvaluator(),
            EmulatorCheckEvaluator(),
            RootCheckEvaluator(),
            BuildTypeCheckEvaluator(),
            SignatureCheckEvaluator(actualSha256 = { certSha256(context) })
        )
        val results = evaluators.map { it.evaluate() }
        return results to RestrictedMode.evaluate(results)
    }

    private fun certSha256(context: Context): String {
        return try {
            @Suppress("DEPRECATION")
            val sig: Signature = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                context.packageManager
                    .getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                    .signingInfo?.apkContentsSigners?.firstOrNull() ?: return ""
            } else {
                context.packageManager
                    .getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
                    .signatures?.firstOrNull() ?: return ""
            }
            val digest = MessageDigest.getInstance("SHA-256").digest(sig.toByteArray())
            digest.joinToString(":") { "%02X".format(it) }
        } catch (_: Exception) {
            ""
        }
    }
}

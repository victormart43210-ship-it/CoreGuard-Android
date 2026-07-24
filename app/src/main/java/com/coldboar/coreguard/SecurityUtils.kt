package com.coldboar.coreguard

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import java.security.MessageDigest

/**
 * Shared security utility functions extracted from Activity-level helpers so
 * they can be called from both legacy Activities and new Compose screens.
 */
object SecurityUtils {

    /**
     * Returns the SHA-256 fingerprint of the first APK signing certificate, or
     * an empty string on failure.
     */
    fun getAppCertSha256(context: Context): String {
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

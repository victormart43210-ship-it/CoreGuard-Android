package com.coldboar.coreguard.guardian.verify

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.coldboar.coreguard.BuildConfig
import com.coldboar.coreguard.SecurityCheckRunner
import com.coldboar.coreguard.guardian.Evidence
import com.coldboar.coreguard.guardian.EvidenceClass
import com.coldboar.coreguard.guardian.InstallationVerification

/**
 * Verify CoreGuard installation identity (Blueprint §14).
 * Signature mismatch ⇒ “does not match official signing identity”, not “malware”.
 */
object InstallationVerifier {

    fun verify(context: Context): InstallationVerification {
        val now = System.currentTimeMillis()
        val installed = SecurityCheckRunner.certSha256(context)
        val expected = BuildConfig.EXPECTED_CERT_SHA256.trim()
        val packageOk = context.packageName == "com.coldboar.coreguard" ||
            context.packageName == "com.coldboar.coreguard.debug"
        val sigOk = when {
            expected.isEmpty() -> false
            installed.isEmpty() -> false
            else -> normalize(installed) == normalize(expected)
        }
        val installer = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(context.packageName)
            }
        }.getOrNull()

        val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionName = pInfo.versionName ?: BuildConfig.VERSION_NAME
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            pInfo.versionCode.toLong()
        }

        val evidence = listOf(
            Evidence(
                id = "verify-cert-$now",
                evidenceClass = EvidenceClass.OBSERVED,
                source = "PackageManager signing certificates",
                summary = if (sigOk) {
                    "Installed signing certificate matches the expected CoreGuard identity."
                } else if (expected.isEmpty()) {
                    "Expected certificate pin is not configured in this build."
                } else {
                    "Installed signing certificate does not match the official CoreGuard signing identity. " +
                        "Forks and unofficial builds are not automatically malware."
                },
                technicalDetail = "installed=$installed expected=$expected",
                collectedAtEpochMillis = now,
                verifiableValue = installed
            ),
            Evidence(
                id = "verify-pkg-$now",
                evidenceClass = EvidenceClass.OBSERVED,
                source = "ApplicationInfo",
                summary = "Package ${context.packageName} · buildType=${BuildConfig.BUILD_TYPE}",
                collectedAtEpochMillis = now,
                verifiableValue = context.packageName
            )
        )

        return InstallationVerification(
            packageNameMatches = packageOk,
            signatureMatches = sigOk,
            expectedCertificateSha256 = expected.ifEmpty { null },
            installedCertificateSha256 = installed.ifEmpty { null },
            installerPackage = installer,
            buildType = BuildConfig.BUILD_TYPE,
            verifiedAtEpochMillis = now,
            evidence = evidence,
            packageName = context.packageName,
            versionName = versionName,
            versionCode = versionCode
        )
    }

    private fun normalize(fp: String): String =
        fp.uppercase().replace(":", "").replace(" ", "")
}

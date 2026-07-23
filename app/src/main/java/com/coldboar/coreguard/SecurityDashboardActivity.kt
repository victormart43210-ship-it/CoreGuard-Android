package com.coldboar.coreguard

import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.coldboar.coreguard.databinding.ActivitySecurityDashboardBinding
import java.security.MessageDigest

/**
 * Security Dashboard screen — View-based implementation.
 *
 * @deprecated This Activity's functionality has been migrated to [HomeScreen]
 *   within the Compose navigation graph. It is no longer launched by
 *   [MainActivity] and will be removed in a future cleanup phase.
 *   Use [HomeScreen] for all new work.
 */
@Deprecated("Superseded by HomeScreen in the Compose NavHost. Do not launch from new code.")
class SecurityDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySecurityDashboardBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySecurityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.security_dashboard_title)

        val evaluators: List<SecurityCheckEvaluator> = listOf(
            SpywareScanEvaluator(),
            DebuggerCheckEvaluator(),
            EmulatorCheckEvaluator(),
            RootCheckEvaluator(),
            BuildTypeCheckEvaluator(),
            SignatureCheckEvaluator(actualSha256 = { getAppCertSha256() })
        )

        val results = evaluators.map { it.evaluate() }
        renderResults(results)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun renderResults(results: List<SecurityCheckResult>) {
        val sb = StringBuilder()
        results.forEach { result ->
            val icon = when (result.state) {
                SecurityCheckState.PASS -> "✅"
                SecurityCheckState.WARN -> "⚠️"
                SecurityCheckState.FAIL -> "❌"
            }
            sb.appendLine("$icon ${result.displayName}")
            sb.appendLine("   ${result.explanation}")
            sb.appendLine()
        }
        binding.tvSecurityResults.text = sb.toString().trim()

        val overallState = when {
            results.any { it.state == SecurityCheckState.FAIL } -> SecurityCheckState.FAIL
            results.any { it.state == SecurityCheckState.WARN } -> SecurityCheckState.WARN
            else -> SecurityCheckState.PASS
        }

        binding.tvOverallStatus.text = when (overallState) {
            SecurityCheckState.PASS -> getString(R.string.status_overall_pass)
            SecurityCheckState.WARN -> getString(R.string.status_overall_warn)
            SecurityCheckState.FAIL -> getString(R.string.status_overall_fail)
        }

        binding.tvOverallStatus.setTextColor(
            getColor(
                when (overallState) {
                    SecurityCheckState.PASS -> R.color.status_pass
                    SecurityCheckState.WARN -> R.color.status_warn
                    SecurityCheckState.FAIL -> R.color.status_fail
                }
            )
        )
    }

    /**
     * Returns the SHA-256 fingerprint of the first signing certificate, or
     * empty string on failure.
     */
    private fun getAppCertSha256(): String {
        return try {
            @Suppress("DEPRECATION")
            val sig: Signature = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
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

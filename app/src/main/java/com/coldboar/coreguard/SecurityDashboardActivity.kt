package com.coldboar.coreguard

import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.coldboar.coreguard.databinding.ActivitySecurityDashboardBinding
import java.security.MessageDigest

/**
 * Security Dashboard screen.
 *
 * Shows a PASS / WARN / FAIL status row for each security check, with a short
 * explanation. Checks are evaluated synchronously on the main thread — each
 * check is designed to be fast (local heuristics only).
 *
 * These checks are best-effort indicators, not security guarantees.
 */
class SecurityDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySecurityDashboardBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySecurityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.security_dashboard_title)

        val evaluators: List<SecurityCheckEvaluator> = listOf(
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
        binding.containerChecks.removeAllViews()

        results.forEach { result ->
            val stateLabel = when (result.state) {
                SecurityCheckState.PASS -> getString(R.string.status_pass)
                SecurityCheckState.WARN -> getString(R.string.status_warn)
                SecurityCheckState.FAIL -> getString(R.string.status_fail)
            }
            val colorRes = when (result.state) {
                SecurityCheckState.PASS -> R.color.status_pass
                SecurityCheckState.WARN -> R.color.status_warn
                SecurityCheckState.FAIL -> R.color.status_fail
            }

            val row = layoutInflater.inflate(
                R.layout.item_security_check,
                binding.containerChecks,
                false
            )
            row.findViewById<TextView>(R.id.tvCheckName).text = result.displayName
            row.findViewById<TextView>(R.id.tvCheckState).apply {
                text = stateLabel
                setTextColor(ContextCompat.getColor(this@SecurityDashboardActivity, colorRes))
            }
            row.findViewById<TextView>(R.id.tvCheckExplanation).text = result.explanation
            binding.containerChecks.addView(row)
        }

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
            ContextCompat.getColor(
                this,
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

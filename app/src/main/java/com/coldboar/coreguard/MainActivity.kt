package com.coldboar.coreguard

import android.os.Bundle
<<<<<<< HEAD
import androidx.activity.compose.setContent
=======
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AnimationUtils
>>>>>>> origin/main
import androidx.appcompat.app.AppCompatActivity
import com.coldboar.coreguard.ui.CoreGuardApp
import com.coldboar.coreguard.ui.theme.CoreGuardTheme

/**
 * Single launcher Activity for the entire app.
 *
<<<<<<< HEAD
 * Sets up the Compose content tree:
 *   MainActivity → CoreGuardTheme → CoreGuardApp → one NavHost
 *
 * All screen navigation is handled inside [CoreGuardApp]. This Activity
 * contains no polling logic, no ViewBinding, and no direct navigation calls.
=======
 * Shows the animated Guardian Score shield (derived from the security checks),
 * device vitals (RAM usage + simulated CPU) and navigation to the Sanctum
 * (Security Dashboard). A lifecycle-safe polling loop updates the memory stats
 * while the activity is visible.
>>>>>>> origin/main
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
<<<<<<< HEAD
        setContent {
            CoreGuardTheme {
                CoreGuardApp()
            }
=======
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val openSanctum = View.OnClickListener {
            startActivity(Intent(this, SecurityDashboardActivity::class.java))
        }
        binding.btnSecurityDashboard.setOnClickListener(openSanctum)
        binding.scoreContainer.setOnClickListener(openSanctum)

        binding.btnNemesisScanner.setOnClickListener {
            startActivity(Intent(this, ThreatScannerActivity::class.java))
        }

        binding.btnNetworkDefenseLab.setOnClickListener {
            startActivity(Intent(this, com.coldboar.coreguard.lab.NetworkDefenseLabActivity::class.java))
        }

        binding.btnUpgradePremium.setOnClickListener {
            subscriptionManager.launchPaywallIfNotShowing(this)
        }

        playEntranceAnimations()
        updateMemoryStats()
    }

    override fun onResume() {
        super.onResume()
        startPolling()
        subscriptionManager.onPaywallDismissed() // reset guard when returning from paywall
        updateGuardianScore()
    }

    override fun onPause() {
        super.onPause()
        stopPolling()
    }

    private fun playEntranceAnimations() {
        val fadeUp = AnimationUtils.loadAnimation(this, R.anim.fade_up)
        binding.tvWordmark.startAnimation(fadeUp)
        binding.tvSubtitle.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_up_delayed))
    }

    /** Evaluates the security checks and animates the Guardian Score shield. */
    private fun updateGuardianScore() {
        val results = SecurityCheckRunner.run(this)
        val score = GuardianScore.compute(results)
        val rank = GuardianScore.rankFor(score)

        val (rankLabel, rankColor) = when (rank) {
            GuardianRank.AEGIS -> R.string.rank_aegis to getColor(R.color.gold)
            GuardianRank.WARDED -> R.string.rank_warded to getColor(R.color.ward_teal)
            GuardianRank.EXPOSED -> R.string.rank_exposed to getColor(R.color.status_warn)
            GuardianRank.BREACHED -> R.string.rank_breached to getColor(R.color.status_fail)
        }

        binding.guardianScoreView.setScore(score, rankColor)
        binding.tvRank.text = getString(rankLabel)
        binding.tvRank.setTextColor(rankColor)
    }

    private fun startPolling() {
        if (isPolling) return
        isPolling = true
        CpuUsageCalculator.reset()
        pollingHandler.post(pollingRunnable)
    }

    private fun stopPolling() {
        isPolling = false
        pollingHandler.removeCallbacks(pollingRunnable)
    }

    private fun updateMemoryStats() {
        val usedRam = MemoryUsageCalculator.getUsedRamBytes(this)
        val totalRam = MemoryUsageCalculator.getTotalRamBytes(this)
        val percent = MemoryUsageCalculator.getUsedRamPercent(this)

        binding.tvRamUsage.text = if (usedRam != null && totalRam != null) {
            "${MemoryUsageCalculator.formatBytes(usedRam)} / ${MemoryUsageCalculator.formatBytes(totalRam)}"
        } else {
            "–"
        }

        binding.tvRamPercent.text = if (percent != null) "$percent%" else "–"
        binding.ramProgress.setProgressCompat(percent ?: 0, true)

        val cpuPercent = CpuUsageCalculator.getUsagePercent()
        binding.tvCpuUsage.text = if (cpuPercent != null) {
            getString(R.string.cpu_usage_value, cpuPercent)
        } else {
            getString(R.string.cpu_measuring_label)
>>>>>>> origin/main
        }
        binding.cpuProgress.setProgressCompat(cpuPercent ?: 0, cpuPercent != null)
    }
}

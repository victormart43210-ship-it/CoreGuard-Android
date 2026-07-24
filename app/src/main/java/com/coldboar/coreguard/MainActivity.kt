package com.coldboar.coreguard

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.coldboar.coreguard.databinding.ActivityMainBinding

/**
 * Main screen.
 *
 * Shows the Guardian Score preview, device vitals, entitlement honesty labels,
 * and restricted-mode banners while keeping CPU explicitly marked as simulated.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val pollingHandler = Handler(Looper.getMainLooper())
    private val pollingIntervalMs = 2_000L
    private var isPolling = false
    private var restrictedActive: Boolean = false

    private val app: CoreGuardApp
        get() = application as CoreGuardApp

    private val subscriptionManager: SubscriptionManager
        get() = app.subscriptionManager

    private val pollingRunnable = object : Runnable {
        override fun run() {
            if (!isPolling) return
            updateMemoryStats()
            pollingHandler.postDelayed(this, pollingIntervalMs)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val openSanctum = View.OnClickListener {
            startActivity(Intent(this, SecurityDashboardActivity::class.java))
        }
        binding.btnSecurityDashboard.setOnClickListener(openSanctum)
        binding.scoreContainer.setOnClickListener(openSanctum)

        binding.btnNemesisScanner.setOnClickListener {
            if (restrictedActive) {
                Toast.makeText(this, R.string.restricted_mode_scanner_blocked, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startActivity(Intent(this, ThreatScannerActivity::class.java))
        }

        binding.btnUpgradePremium.setOnClickListener {
            if (!RestrictedMode.canLaunchPaywall(restrictedActive)) {
                Toast.makeText(this, R.string.restricted_mode_paywall_blocked, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            subscriptionManager.launchPaywallIfNotShowing(this)
        }

        playEntranceAnimations()
        updateMemoryStats()
        refreshSecurityAndEntitlementUi()
    }

    override fun onResume() {
        super.onResume()
        startPolling()
        subscriptionManager.onPaywallDismissed()
        if (app.billingProvider.backend == BillingBackend.PLAY) {
            app.billingProvider.refreshPurchases {
                app.persistEntitlementCache()
                refreshSecurityAndEntitlementUi()
            }
        } else {
            app.persistEntitlementCache()
            refreshSecurityAndEntitlementUi()
        }
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

    private fun refreshSecurityAndEntitlementUi() {
        val (results, decision) = SecuritySnapshot.capture(this)
        restrictedActive = decision.active

        binding.tvRestrictedBanner.visibility = if (decision.active) View.VISIBLE else View.GONE
        if (decision.active) {
            binding.tvRestrictedBanner.text = getString(R.string.restricted_mode_banner)
        }

        updateGuardianScore(results)

        val billing = app.billingProvider
        val isPremium = billing.isPremium()
        binding.tvEntitlementBanner.text = when (billing.backend) {
            BillingBackend.DEMO -> getString(
                if (isPremium) R.string.entitlement_demo_premium else R.string.entitlement_demo_free
            )
            BillingBackend.PLAY -> getString(
                if (isPremium) R.string.entitlement_play_premium else R.string.entitlement_play_free
            )
        }

        binding.btnUpgradePremium.setText(
            if (billing.backend == BillingBackend.DEMO) {
                R.string.btn_upgrade_premium_demo
            } else {
                R.string.btn_upgrade_premium_play
            }
        )
    }

    /** Evaluates the security checks and animates the Guardian Score shield. */
    private fun updateGuardianScore(results: List<SecurityCheckResult>) {
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

        binding.tvCpuUsage.text = getString(R.string.cpu_simulated_label)
        binding.cpuProgress.setProgressCompat(0, false)
    }
}

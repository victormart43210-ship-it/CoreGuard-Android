package com.coldboar.coreguard

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.coldboar.coreguard.databinding.ActivityMainBinding

/**
 * Main screen.
 *
 * Displays device health (RAM + simulated CPU), entitlement honesty banners,
 * and restricted-mode state derived from local security checks.
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

        binding.btnSecurityDashboard.setOnClickListener {
            startActivity(Intent(this, SecurityDashboardActivity::class.java))
        }

        binding.btnUpgradePremium.setOnClickListener {
            if (!RestrictedMode.canLaunchPaywall(restrictedActive)) {
                Toast.makeText(this, R.string.restricted_mode_paywall_blocked, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            subscriptionManager.launchPaywallIfNotShowing(this)
        }

        binding.btnUpgradePremium.setText(
            if (app.billingProvider.backend == BillingBackend.DEMO) {
                R.string.btn_upgrade_premium_demo
            } else {
                R.string.btn_upgrade_premium_play
            }
        )

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
        binding.tvCpuUsage.text = getString(R.string.cpu_simulated_label)
    }

    private fun refreshSecurityAndEntitlementUi() {
        val (_, decision) = SecuritySnapshot.capture(this)
        restrictedActive = decision.active
        binding.tvRestrictedBanner.visibility = if (decision.active) View.VISIBLE else View.GONE
        if (decision.active) {
            binding.tvRestrictedBanner.text = getString(R.string.restricted_mode_banner)
        }

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
    }
}

package com.coldboar.coreguard

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.coldboar.coreguard.databinding.ActivityMainBinding

/**
 * Main screen.
 *
 * Displays a summary of device health (RAM usage + simulated CPU) and provides
 * navigation to the Security Dashboard. A lifecycle-safe polling loop updates
 * the memory stats while the activity is visible.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val pollingHandler = Handler(Looper.getMainLooper())
    private val pollingIntervalMs = 2_000L
    private var isPolling = false

    private val billingProvider: BillingProvider = DemoBillingProvider()
    private val subscriptionManager = SubscriptionManager(billingProvider)

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

        binding.btnPegasusScanner.setOnClickListener {
            startActivity(Intent(this, ThreatScannerActivity::class.java))
        }

        binding.btnUpgradePremium.setOnClickListener {
            subscriptionManager.launchPaywallIfNotShowing(this)
        }

        updateMemoryStats()
    }

    override fun onResume() {
        super.onResume()
        startPolling()
        subscriptionManager.onPaywallDismissed() // reset guard when returning from paywall
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

        // CPU value is explicitly simulated – no real CPU measurement is performed.
        binding.tvCpuUsage.text = getString(R.string.cpu_simulated_label)
    }
}

package com.coldboar.coreguard

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.coldboar.coreguard.databinding.ActivityPaywallBinding

/**
 * Paywall screen ("Forge Premium").
 *
 * **DEMO ONLY** – displays a simulated upgrade prompt. No real payment is
 * processed here. Replace the demo purchase flow with a real Google Play
 * Billing integration before distributing.
 *
 * The [SubscriptionManager] is responsible for preventing duplicate launches
 * of this activity; call [SubscriptionManager.onPaywallDismissed] when this
 * activity exits.
 */
class PaywallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPaywallBinding

    // In production, inject a real BillingProvider via DI or a ViewModel.
    private val billing: BillingProvider = DemoBillingProvider()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaywallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        binding.btnSubscribe.setOnClickListener {
            // DEMO: simulates a purchase – no real payment is made.
            billing.launchPurchaseFlow(PRODUCT_ID_PREMIUM) { result ->
                when (result) {
                    is PurchaseResult.Success -> {
                        binding.tvPaywallStatus.text = getString(R.string.paywall_purchase_success_demo)
                        finish()
                    }
                    is PurchaseResult.Cancelled -> {
                        binding.tvPaywallStatus.text = getString(R.string.paywall_purchase_cancelled)
                    }
                    is PurchaseResult.Error -> {
                        binding.tvPaywallStatus.text =
                            getString(R.string.paywall_purchase_error, result.message)
                    }
                }
            }
        }

        binding.btnClose.setOnClickListener { finish() }
    }

    companion object {
        /** Play Console product ID placeholder – update before publishing. */
        const val PRODUCT_ID_PREMIUM = "coreguard_premium_monthly"
    }
}

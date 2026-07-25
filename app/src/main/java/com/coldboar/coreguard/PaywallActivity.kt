package com.coldboar.coreguard

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.coldboar.coreguard.databinding.ActivityPaywallBinding

/**
 * Legacy paywall Activity kept for deep-link / older navigation paths.
 *
 * Uses [PlayBillingProvider] (not the demo provider). Prefer the Compose
 * Settings premium card for the primary purchase UX.
 */
class PaywallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPaywallBinding
    private lateinit var billing: PlayBillingProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaywallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        billing = PlayBillingProvider(applicationContext).also { it.attach(this) }

        if (billing.isPremium()) {
            binding.tvPaywallStatus.text = getString(R.string.paywall_already_premium)
            binding.btnSubscribe.isEnabled = false
        }

        binding.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        binding.btnSubscribe.setOnClickListener {
            billing.launchPurchaseFlow(PRODUCT_ID_PREMIUM) { result ->
                when (result) {
                    is PurchaseResult.Success -> {
                        binding.tvPaywallStatus.text = getString(R.string.paywall_purchase_success)
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

    override fun onDestroy() {
        if (::billing.isInitialized) {
            billing.detach()
            if (isFinishing) billing.destroy()
        }
        super.onDestroy()
    }

    companion object {
        const val PRODUCT_ID_PREMIUM = "coreguard_premium_monthly"
    }
}

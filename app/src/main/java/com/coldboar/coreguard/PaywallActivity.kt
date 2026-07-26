package com.coldboar.coreguard

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.coldboar.coreguard.databinding.ActivityPaywallBinding

/**
 * Legacy paywall Activity. Uses [PlayBillingProvider] for real purchases.
 * Prefer the Compose Settings Premium card for the primary purchase UX.
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
        } else {
            val price = billing.premiumPriceLabel()
            if (price.isNotBlank()) {
                binding.btnSubscribe.text = getString(R.string.paywall_btn_subscribe_priced, price)
            }
        }

        binding.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        binding.btnSubscribe.setOnClickListener {
            billing.launchPurchaseFlow(EntitlementPolicy.PREMIUM_PRODUCT_ID) { result ->
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
        SubscriptionManager(billing).onPaywallDismissed()
        if (::billing.isInitialized) {
            billing.detach()
            if (isFinishing) billing.destroy()
        }
        super.onDestroy()
    }
}

package com.coldboar.coreguard

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.coldboar.coreguard.databinding.ActivityPaywallBinding

/**
 * Paywall screen for CoreGuard Premium via Google Play Billing.
 *
 * Uses the shared [PlayBillingProvider] from [CoreGuardApplication] so entitlement
 * state stays consistent with Settings and other premium gates.
 * Prefer the Compose Settings Premium card for the primary purchase UX.
 */
class PaywallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPaywallBinding

    private val billing: PlayBillingProvider
        get() = CoreGuardApplication.require().billingProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaywallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        billing.attach(this)

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
            billing.launchPurchaseFlow(BillingProvider.PREMIUM_PRODUCT_ID) { result ->
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

    override fun onResume() {
        super.onResume()
        billing.attach(this)
    }

    override fun onDestroy() {
        SubscriptionManager(billing).onPaywallDismissed()
        billing.detach()
        super.onDestroy()
    }
}

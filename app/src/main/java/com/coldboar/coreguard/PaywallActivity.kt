package com.coldboar.coreguard

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.coldboar.coreguard.databinding.ActivityPaywallBinding

/**
 * Paywall screen.
 *
 * Behavior depends on the active [BillingProvider]:
 * - [DemoBillingProvider] (debug): simulated unlock — not a real purchase.
 * - [PlayBillingProvider] (release): Google Play purchase sheet for
 *   [PRODUCT_ID_PREMIUM]. Client-side success is not server verification.
 *
 * The [SubscriptionManager] prevents duplicate launches of this activity.
 */
class PaywallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPaywallBinding

    private val billing: BillingProvider
        get() = (application as CoreGuardApp).billingProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaywallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.paywall_title)

        val isDemo = billing.backend == BillingBackend.DEMO
        binding.tvPaywallDisclaimer.text = getString(
            if (isDemo) R.string.paywall_demo_disclaimer else R.string.paywall_play_disclaimer
        )
        binding.btnSubscribe.text = getString(
            if (isDemo) R.string.paywall_btn_subscribe_demo else R.string.paywall_btn_subscribe_play
        )

        binding.btnSubscribe.setOnClickListener {
            billing.launchPurchaseFlow(this, PRODUCT_ID_PREMIUM) { result ->
                when (result) {
                    is PurchaseResult.Success -> {
                        binding.tvPaywallStatus.text = getString(
                            if (isDemo) {
                                R.string.paywall_purchase_success_demo
                            } else {
                                R.string.paywall_purchase_success_play
                            }
                        )
                        finish()
                    }
                    is PurchaseResult.Cancelled -> {
                        binding.tvPaywallStatus.text =
                            getString(R.string.paywall_purchase_cancelled)
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

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    companion object {
        /** Must match the subscription product ID configured in Play Console. */
        const val PRODUCT_ID_PREMIUM = "coreguard_premium_monthly"
    }
}

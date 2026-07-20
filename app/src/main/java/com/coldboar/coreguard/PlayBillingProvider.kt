package com.coldboar.coreguard

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

/**
 * Google Play Billing Library integration.
 *
 * Connects [BillingClient], queries subscription product details, launches the
 * Play purchase sheet, acknowledges purchases, and caches premium locally.
 *
 * **Honesty limits**
 * - [isPremium] reflects **client-side** Play purchase state only.
 * - This is **not** server-side purchase-token verification. Add backend
 *   verification with the Google Play Developer API before trusting entitlements
 *   in production.
 * - Requires a Play Console subscription product (default id:
 *   [PaywallActivity.PRODUCT_ID_PREMIUM]) and a build installed via Play
 *   (internal testing track, etc.). Sideloaded APKs often cannot complete purchases.
 */
class PlayBillingProvider(
    context: Context,
    private val mainHandler: Handler = Handler(Looper.getMainLooper())
) : BillingProvider, PurchasesUpdatedListener {

    override val backend: BillingBackend = BillingBackend.PLAY

    private val appContext = context.applicationContext

    @Volatile
    private var premiumCached: Boolean = false

    @Volatile
    private var connected: Boolean = false

    private var pendingResult: ((PurchaseResult) -> Unit)? = null

    private val billingClient: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .build()

    init {
        startConnection()
    }

    override fun isPremium(): Boolean = premiumCached

    override fun launchPurchaseFlow(
        activity: Activity?,
        productId: String,
        onResult: (PurchaseResult) -> Unit
    ) {
        if (activity == null) {
            onResult(PurchaseResult.Error("Activity is required to launch Google Play Billing."))
            return
        }
        if (!connected || !billingClient.isReady) {
            onResult(
                PurchaseResult.Error(
                    "Play Billing is not connected. Use a Play-installed build and check network."
                )
            )
            return
        }

        pendingResult = onResult

        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(productId)
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                deliver(PurchaseResult.Error(billingMessage(billingResult)))
                return@queryProductDetailsAsync
            }

            val details = productDetailsList.firstOrNull()
            if (details == null) {
                deliver(
                    PurchaseResult.Error(
                        "Subscription product \"$productId\" not found in Play Console for this app."
                    )
                )
                return@queryProductDetailsAsync
            }

            val flowParams = buildSubscriptionFlowParams(details)
            if (flowParams == null) {
                deliver(PurchaseResult.Error("No subscription offer available for \"$productId\"."))
                return@queryProductDetailsAsync
            }

            val launchResult = billingClient.launchBillingFlow(activity, flowParams)
            if (launchResult.responseCode != BillingClient.BillingResponseCode.OK) {
                deliver(PurchaseResult.Error(billingMessage(launchResult)))
            }
            // Success / cancel delivered via onPurchasesUpdated.
        }
    }

    override fun refreshPurchases(onComplete: (() -> Unit)?) {
        if (!connected || !billingClient.isReady) {
            onComplete?.invoke()
            return
        }

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                updatePremiumFromPurchases(purchases)
                acknowledgeIfNeeded(purchases)
            }
            mainHandler.post { onComplete?.invoke() }
        }
    }

    override fun destroy() {
        pendingResult = null
        connected = false
        billingClient.endConnection()
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val list = purchases.orEmpty()
                updatePremiumFromPurchases(list)
                acknowledgeIfNeeded(list)
                deliver(PurchaseResult.Success)
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                deliver(PurchaseResult.Cancelled)
            }
            else -> {
                deliver(PurchaseResult.Error(billingMessage(billingResult)))
            }
        }
    }

    private fun startConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                connected = billingResult.responseCode == BillingClient.BillingResponseCode.OK
                if (connected) {
                    refreshPurchases(null)
                }
            }

            override fun onBillingServiceDisconnected() {
                connected = false
            }
        })
    }

    private fun buildSubscriptionFlowParams(details: ProductDetails): BillingFlowParams? {
        val offerToken = details.subscriptionOfferDetails
            ?.firstOrNull()
            ?.offerToken
            ?: return null

        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(offerToken)
            .build()

        return BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()
    }

    private fun updatePremiumFromPurchases(purchases: List<Purchase>) {
        premiumCached = purchases.any { purchase ->
            purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                purchase.products.contains(PaywallActivity.PRODUCT_ID_PREMIUM)
        }
    }

    private fun acknowledgeIfNeeded(purchases: List<Purchase>) {
        purchases
            .filter {
                it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged
            }
            .forEach { purchase ->
                val params = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                billingClient.acknowledgePurchase(params) { /* best-effort */ }
            }
    }

    private fun deliver(result: PurchaseResult) {
        val callback = pendingResult
        pendingResult = null
        mainHandler.post { callback?.invoke(result) }
    }

    private fun billingMessage(result: BillingResult): String {
        val debug = result.debugMessage.takeIf { it.isNotBlank() }
        return if (debug != null) {
            "Play Billing error ${result.responseCode}: $debug"
        } else {
            "Play Billing error ${result.responseCode}"
        }
    }
}

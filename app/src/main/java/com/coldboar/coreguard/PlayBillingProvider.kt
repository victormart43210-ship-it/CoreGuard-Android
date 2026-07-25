package com.coldboar.coreguard

import android.app.Activity
import android.content.Context
import android.util.Log
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Production [BillingProvider] backed by the Google Play Billing Library.
 *
 * Lifecycle:
 * - Call [attach] from your Activity's `onCreate` / `onResume`.
 * - Call [detach] from your Activity's `onDestroy`.
 * - The [BillingClient] reconnects automatically on disconnect.
 *
 * Premium entitlement is determined by finding an active, acknowledged
 * subscription purchase matching [PREMIUM_PRODUCT_ID].
 */
class PlayBillingProvider(
    private val context: Context
) : BillingProvider {

    companion object {
        private const val TAG = "PlayBilling"
        const val PREMIUM_PRODUCT_ID = "coreguard_premium_monthly"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Volatile
    private var premiumCached: Boolean = false

    @Volatile
    private var cachedProductDetails: ProductDetails? = null

    private var pendingPurchaseCallback: ((PurchaseResult) -> Unit)? = null
    private var currentActivity: Activity? = null

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { handlePurchase(it) }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                pendingPurchaseCallback?.invoke(PurchaseResult.Cancelled)
                pendingPurchaseCallback = null
            }
            else -> {
                pendingPurchaseCallback?.invoke(
                    PurchaseResult.Error("Billing error: ${billingResult.debugMessage}")
                )
                pendingPurchaseCallback = null
            }
        }
    }

    private val billingClient: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    /** Call from Activity.onCreate or onResume to establish/restore the billing connection. */
    fun attach(activity: Activity) {
        currentActivity = activity
        if (!billingClient.isReady) {
            connect()
        }
    }

    /** Call from Activity.onDestroy to release the Activity reference. */
    fun detach() {
        currentActivity = null
    }

    /** Disconnect and release all resources. Call when the owning component is destroyed. */
    fun destroy() {
        currentActivity = null
        billingClient.endConnection()
    }

    private fun connect() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing client connected.")
                    queryExistingPurchases()
                    queryProductDetails()
                } else {
                    Log.w(TAG, "Billing setup failed: ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing service disconnected – will retry on next attach.")
            }
        })
    }

    private fun queryExistingPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        billingClient.queryPurchasesAsync(params) { _, purchases ->
            val hasActive = purchases.any { purchase ->
                purchase.products.contains(PREMIUM_PRODUCT_ID) &&
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED
            }
            premiumCached = hasActive
            Log.d(TAG, "Existing purchases queried – premium=$hasActive")
        }
    }

    private fun queryProductDetails() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PREMIUM_PRODUCT_ID)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { _, productDetailsList ->
            cachedProductDetails = productDetailsList.firstOrNull()
            Log.d(TAG, "Product details fetched: ${cachedProductDetails?.name}")
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {
                val ackParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                scope.launch {
                    billingClient.acknowledgePurchase(ackParams) { result ->
                        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                            premiumCached = true
                            pendingPurchaseCallback?.invoke(PurchaseResult.Success)
                            pendingPurchaseCallback = null
                            Log.d(TAG, "Purchase acknowledged – premium unlocked.")
                        } else {
                            pendingPurchaseCallback?.invoke(
                                PurchaseResult.Error("Acknowledgement failed: ${result.debugMessage}")
                            )
                            pendingPurchaseCallback = null
                        }
                    }
                }
            } else {
                premiumCached = true
                pendingPurchaseCallback?.invoke(PurchaseResult.Success)
                pendingPurchaseCallback = null
            }
        }
    }

    // -----------------------------------------------------------------------
    // BillingProvider implementation
    // -----------------------------------------------------------------------

    override fun isPremium(): Boolean = premiumCached

    override fun launchPurchaseFlow(productId: String, onResult: (PurchaseResult) -> Unit) {
        val activity = currentActivity
        if (activity == null || activity.isFinishing) {
            onResult(PurchaseResult.Error("No active screen to show billing UI."))
            return
        }

        if (!billingClient.isReady) {
            onResult(PurchaseResult.Error("Billing service not ready. Please try again."))
            connect()
            return
        }

        val details = cachedProductDetails
        if (details == null) {
            onResult(PurchaseResult.Error("Product details unavailable. Please try again."))
            queryProductDetails()
            return
        }

        val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken
        if (offerToken == null) {
            onResult(PurchaseResult.Error("No subscription offer available in your region."))
            return
        }

        pendingPurchaseCallback = onResult

        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(offerToken)
            .build()

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()

        val result = billingClient.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            pendingPurchaseCallback = null
            onResult(PurchaseResult.Error("Could not open Play Store: ${result.debugMessage}"))
        }
    }
}

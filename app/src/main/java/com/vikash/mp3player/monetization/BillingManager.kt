package com.vikash.mp3player.monetization

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.vikash.mp3player.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One-time "Remove ads" purchase through Google Play Billing. Create a one-time product with
 * id [BuildConfig.REMOVE_ADS_PRODUCT_ID] (default "ampli_pro", the one-time Ampli Pro unlock) in Play Console under
 * Monetize > Products > In-app products.
 *
 * The cached flag lets ad-free users start offline; Play is re-checked on every launch, so a
 * refund turns ads back on.
 */
class BillingManager(context: Context) : PurchasesUpdatedListener {

    private val prefs = context.getSharedPreferences("billing", Context.MODE_PRIVATE)

    private val _adsRemoved = MutableStateFlow(prefs.getBoolean(KEY_ADS_REMOVED, false))
    val adsRemoved: StateFlow<Boolean> = _adsRemoved.asStateFlow()

    /** Localised price such as "₹149.00", or null until Play answers. */
    private val _price = MutableStateFlow<String?>(null)
    val price: StateFlow<String?> = _price.asStateFlow()

    private var productDetails: ProductDetails? = null

    private val client = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    fun start() {
        if (client.isReady) return
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode != BillingClient.BillingResponseCode.OK) return
                queryProduct()
                refreshPurchases()
            }

            override fun onBillingServiceDisconnected() {
                // Reconnected lazily on the next start() or purchase attempt.
            }
        })
    }

    fun purchaseRemoveAds(activity: Activity) {
        val details = productDetails ?: return start()
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).build())
            )
            .build()
        client.launchBillingFlow(activity, params)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            purchases?.forEach(::handlePurchase)
        } else if (result.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            refreshPurchases()
        }
    }

    private fun queryProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(BuildConfig.REMOVE_ADS_PRODUCT_ID)
                        .setProductType(ProductType.INAPP)
                        .build()
                )
            )
            .build()
        client.queryProductDetailsAsync(params) { result, list ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            productDetails = list.firstOrNull()
            _price.value = productDetails?.oneTimePurchaseOfferDetails?.formattedPrice
        }
    }

    private fun refreshPurchases() {
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(ProductType.INAPP).build()
        ) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            val owned = purchases.any { it.ownsRemoveAds() }
            setAdsRemoved(owned)
            purchases.forEach(::handlePurchase)
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (!purchase.ownsRemoveAds()) return
        setAdsRemoved(true)
        // Purchases not acknowledged within 3 days are refunded automatically by Play.
        if (!purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
            client.acknowledgePurchase(params) { }
        }
    }

    private fun Purchase.ownsRemoveAds() =
        purchaseState == Purchase.PurchaseState.PURCHASED &&
            BuildConfig.REMOVE_ADS_PRODUCT_ID in products

    /** Sideloaded test builds can't reach Play Billing, so they get a switch to try Pro. */
    fun setTestPro(value: Boolean) {
        if (BuildConfig.DEBUG) setAdsRemoved(value)
    }

    private fun setAdsRemoved(value: Boolean) {
        _adsRemoved.value = value
        prefs.edit().putBoolean(KEY_ADS_REMOVED, value).apply()
    }

    private companion object {
        const val KEY_ADS_REMOVED = "ads_removed"
    }
}

package com.vikash.mp3player.monetization

import android.os.SystemClock
import java.lang.ref.WeakReference
import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
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

    /** Tip product id to its localised price, filled once Play answers. */
    private val _tipPrices = MutableStateFlow<Map<String, String>>(emptyMap())
    val tipPrices: StateFlow<Map<String, String>> = _tipPrices.asStateFlow()
    private var tipDetails: Map<String, ProductDetails> = emptyMap()

    /** True while a payment waits for the bank (common with UPI); the UI says "Payment pending". */
    private val _pending = MutableStateFlow(false)
    val pending: StateFlow<Boolean> = _pending.asStateFlow()

    /** Goes up by one each time a tip goes through, so the UI can say thank you. */
    private val _tipsReceived = MutableStateFlow(0)
    val tipsReceived: StateFlow<Int> = _tipsReceived.asStateFlow()

    private val client = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        // Billing 8: reconnects by itself when Play's service drops, so a tap never hits a dead client.
        .enableAutoServiceReconnection()
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

    /** What a buy tap did: opened Play's sheet, is waiting for Play's price, or Play has no such product. */
    enum class Buy { OPENED, LOADING, UNAVAILABLE }

    // A tap that came before Play answered: the sheet opens as soon as the price arrives (within 15 s).
    private var waitingFor: String? = null
    private var waitingActivity: WeakReference<Activity>? = null
    private var waitingSince = 0L
    private var proQueried = false
    private var tipsQueried = false

    /** Opens Google Play's payment sheet for Ampli Pro. */
    fun purchaseRemoveAds(activity: Activity): Buy =
        buy(activity, BuildConfig.REMOVE_ADS_PRODUCT_ID, productDetails, proQueried)

    /** Opens Play's payment sheet for a "buy the developer a chai" tip. */
    fun purchaseTip(activity: Activity, productId: String): Buy =
        buy(activity, productId, tipDetails[productId], tipsQueried)

    private fun buy(activity: Activity, id: String, details: ProductDetails?, queried: Boolean): Buy {
        if (details != null) { launch(activity, details); return Buy.OPENED }
        if (queried && client.isReady) return Buy.UNAVAILABLE
        waitingFor = id
        waitingActivity = WeakReference(activity)
        waitingSince = SystemClock.elapsedRealtime()
        if (client.isReady) queryProduct() else start()
        return Buy.LOADING
    }

    private fun launch(activity: Activity, details: ProductDetails) {
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).build())
            )
            .build()
        client.launchBillingFlow(activity, params)
    }

    /** Called when Play's product answer arrives: opens the sheet for a tap that was waiting on it. */
    private fun launchWaiting() {
        val id = waitingFor ?: return
        val details = if (id == BuildConfig.REMOVE_ADS_PRODUCT_ID) productDetails else tipDetails[id]
        val activity = waitingActivity?.get()
        if (details == null || activity == null) return
        waitingFor = null
        waitingActivity = null
        if (SystemClock.elapsedRealtime() - waitingSince > 15_000 || activity.isFinishing || activity.isDestroyed) return
        activity.runOnUiThread { launch(activity, details) }
    }

    /** "Restore purchase": asks Play again which products this Google account owns. */
    fun restore() {
        if (client.isReady) refreshPurchases() else start()
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
        client.queryProductDetailsAsync(params) { result, found ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            val list = found.productDetailsList
            productDetails = list.firstOrNull()
            proQueried = true
            _price.value = productDetails?.oneTimePurchaseOfferDetails?.formattedPrice
            launchWaiting()
        }
        val tipParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                TIP_IDS.map {
                    QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(ProductType.INAPP).build()
                }
            )
            .build()
        client.queryProductDetailsAsync(tipParams) { result, found ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            val list = found.productDetailsList
            tipDetails = list.associateBy { it.productId }
            tipsQueried = true
            launchWaiting()
            _tipPrices.value = list.mapNotNull { d -> d.oneTimePurchaseOfferDetails?.formattedPrice?.let { d.productId to it } }.toMap()
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
        // UPI and cash payments can wait for the bank; nothing unlocks until Play says PURCHASED.
        if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
            _pending.value = true
            return
        }
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) _pending.value = false
        if (purchase.isTip()) {
            // Tips are consumable, so the same tip can be bought again later.
            val params = ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
            client.consumeAsync(params) { result, _ ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _tipsReceived.value += 1
                    report("tip", purchase)
                }
            }
            return
        }
        if (!purchase.ownsRemoveAds()) return
        setAdsRemoved(true)
        // Purchases not acknowledged within 3 days are refunded automatically by Play.
        if (!purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
            client.acknowledgePurchase(params) { result ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) report("pro", purchase)
            }
        }
    }

    /** Tells the Ampli Owner app about a completed purchase (skipped when no server is set). */
    private fun report(kind: String, purchase: Purchase) {
        val sku = purchase.products.firstOrNull() ?: return
        com.vikash.mp3player.data.OwnerHooks.purchase(kind, sku, purchase.purchaseToken)
    }

    private fun Purchase.ownsRemoveAds() =
        purchaseState == Purchase.PurchaseState.PURCHASED &&
            BuildConfig.REMOVE_ADS_PRODUCT_ID in products

    private fun Purchase.isTip() =
        purchaseState == Purchase.PurchaseState.PURCHASED && products.any { it in TIP_IDS }

    /** Sideloaded test builds can't reach Play Billing, so they get a switch to try Pro. */
    fun setTestPro(value: Boolean) {
        if (BuildConfig.DEBUG) setAdsRemoved(value)
    }

    private fun setAdsRemoved(value: Boolean) {
        _adsRemoved.value = value
        prefs.edit().putBoolean(KEY_ADS_REMOVED, value).apply()
    }

    companion object {
        private const val KEY_ADS_REMOVED = "ads_removed"
        val TIP_IDS = listOf(BuildConfig.TIP_SMALL_ID, BuildConfig.TIP_LARGE_ID, BuildConfig.TIP_XL_ID)
    }
}

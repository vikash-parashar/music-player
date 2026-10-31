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
import com.vikash.mp3player.R
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

    /** Closed-test builds give testers Pro without a purchase (see TESTER_PRO in build.gradle.kts). */
    private val testerPro: Boolean
        get() = BuildConfig.TESTER_PRO && System.currentTimeMillis() < BuildConfig.TESTER_PRO_UNTIL

    private val _adsRemoved = MutableStateFlow(prefs.getBoolean(KEY_ADS_REMOVED, false) || testerPro)
    val adsRemoved: StateFlow<Boolean> = _adsRemoved.asStateFlow()

    /** Localised price such as "₹149.00", or null until Play answers. */
    private val _price = MutableStateFlow<String?>(null)
    val price: StateFlow<String?> = _price.asStateFlow()

    private var productDetails: ProductDetails? = null

    /** Tip product id to its localised price, filled once Play answers. */
    private val _tipPrices = MutableStateFlow<Map<String, String>>(emptyMap())
    val tipPrices: StateFlow<Map<String, String>> = _tipPrices.asStateFlow()
    private var tipDetails: Map<String, ProductDetails> = emptyMap()

    /** Ampli Stories Pass, a monthly subscription that opens every Stories episode. */
    private val _storiesPass = MutableStateFlow(prefs.getBoolean(KEY_STORIES_PASS, false) || testerPro)
    val storiesPass: StateFlow<Boolean> = _storiesPass.asStateFlow()

    /** Localised monthly price of the Stories Pass, such as "₹49.00", or null until Play answers. */
    private val _passPrice = MutableStateFlow<String?>(null)
    val passPrice: StateFlow<String?> = _passPrice.asStateFlow()
    private var passDetails: ProductDetails? = null
    private var passQueried = false

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
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    // No Play services, or Play refused: answer a waiting tap instead of leaving "Loading…".
                    settleWaiting(forPro = true)
                    settleWaiting(forPro = false)
                    return
                }
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

    /** Opens Play's subscription sheet for the Stories Pass. */
    fun purchaseStoriesPass(activity: Activity): Buy {
        val details = passDetails
        val offer = details?.subscriptionOfferDetails?.firstOrNull()?.offerToken
        if (details != null && offer != null) {
            val params = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(details).setOfferToken(offer).build()
                    )
                )
                .build()
            client.launchBillingFlow(activity, params)
            return Buy.OPENED
        }
        if (passQueried && client.isReady) return Buy.UNAVAILABLE
        if (client.isReady) queryProduct() else start()
        return Buy.LOADING
    }

    private fun buy(activity: Activity, id: String, details: ProductDetails?, queried: Boolean): Buy {
        if (details != null) { launch(activity, details); return Buy.OPENED }
        if (queried && client.isReady) return Buy.UNAVAILABLE
        waitingFor = id
        waitingActivity = WeakReference(activity)
        waitingSince = SystemClock.elapsedRealtime()
        // If Play never answers within 15 s, say so rather than leaving the tap hanging.
        val since = waitingSince
        timeout.postDelayed({
            if (waitingFor == id && waitingSince == since) settleWaiting(forPro = id == BuildConfig.REMOVE_ADS_PRODUCT_ID, timedOut = true)
        }, 15_000)
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

    private val timeout = android.os.Handler(android.os.Looper.getMainLooper())

    /**
     * Called when Play answers a product query (or fails to, or takes over 15 s): opens the sheet
     * for a tap that was waiting on it, or says the product is unavailable instead of leaving the
     * tap without any reply.
     */
    private fun settleWaiting(forPro: Boolean, timedOut: Boolean = false) {
        val id = waitingFor ?: return
        if ((id == BuildConfig.REMOVE_ADS_PRODUCT_ID) != forPro) return
        val details = if (forPro) productDetails else tipDetails[id]
        val activity = waitingActivity?.get()
        waitingFor = null
        waitingActivity = null
        if (activity == null || activity.isFinishing || activity.isDestroyed) return
        // Too late to pop a payment sheet up out of nowhere; the message still goes out.
        val late = timedOut || SystemClock.elapsedRealtime() - waitingSince > 15_000
        activity.runOnUiThread {
            if (details != null && !late) {
                launch(activity, details)
            } else {
                val msg = when {
                    !forPro -> R.string.tip_unavailable
                    BuildConfig.DEBUG -> R.string.pro_purchase_test_build
                    else -> R.string.pro_purchase_failed
                }
                android.widget.Toast.makeText(activity, msg, android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    /** "Restore purchase": asks Play again which products this Google account owns. */
    fun restore() {
        if (client.isReady) refreshPurchases() else start()
    }

    /** Re-checks purchases when the app comes back (a UPI payment may have cleared, failed or been cancelled meanwhile). */
    fun refresh() {
        if (client.isReady) refreshPurchases()
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            purchases?.forEach(::handlePurchase)
            // "Payment pending" follows Play's full list, so a chai tip can't hide a Pro payment still waiting.
            if (purchases?.any { it.purchaseState == Purchase.PurchaseState.PURCHASED } == true) refreshPurchases()
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
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                productDetails = found.productDetailsList.firstOrNull()
                proQueried = true
                _price.value = productDetails?.oneTimePurchaseOfferDetails?.formattedPrice
            }
            settleWaiting(forPro = true)
        }
        val passParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(STORIES_PASS_ID)
                        .setProductType(ProductType.SUBS)
                        .build()
                )
            )
            .build()
        client.queryProductDetailsAsync(passParams) { result, found ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                passDetails = found.productDetailsList.firstOrNull()
                passQueried = true
                _passPrice.value = passDetails?.subscriptionOfferDetails?.firstOrNull()
                    ?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice
            }
        }
        val tipParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                TIP_IDS.map {
                    QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(ProductType.INAPP).build()
                }
            )
            .build()
        client.queryProductDetailsAsync(tipParams) { result, found ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val list = found.productDetailsList
                tipDetails = list.associateBy { it.productId }
                tipsQueried = true
                _tipPrices.value = list.mapNotNull { d -> d.oneTimePurchaseOfferDetails?.formattedPrice?.let { d.productId to it } }.toMap()
            }
            settleWaiting(forPro = false)
        }
    }

    private fun refreshPurchases() {
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(ProductType.INAPP).build()
        ) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            val owned = purchases.any { it.ownsRemoveAds() }
            // Debug builds keep the test switch on; Play never knows about it.
            setAdsRemoved(owned || testerPro || (BuildConfig.DEBUG && prefs.getBoolean(KEY_TEST_PRO, false)))
            // Play drops a cancelled or failed UPI payment from this list, so "Payment pending" clears here.
            _pending.value = purchases.any { it.purchaseState == Purchase.PurchaseState.PENDING }
            purchases.forEach(::handlePurchase)
        }
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(ProductType.SUBS).build()
        ) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            // An expired or cancelled-and-lapsed subscription drops out of this list, so the Pass closes again.
            setStoriesPass(purchases.any { it.ownsStoriesPass() } || testerPro || (BuildConfig.DEBUG && prefs.getBoolean(KEY_TEST_PRO, false)))
            purchases.forEach(::handlePurchase)
        }
    }

    /** Tokens with an acknowledge or consume call still running. */
    private val inFlight = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    private fun handlePurchase(purchase: Purchase) {
        // UPI and cash payments can wait for the bank; nothing unlocks until Play says PURCHASED.
        if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
            _pending.value = true
            return
        }
        if (purchase.isTip()) {
            // The purchase callback and the resume re-check can both see the same tip: consume it once.
            if (!inFlight.add(purchase.purchaseToken)) return
            // Tips are consumable, so the same tip can be bought again later.
            val params = ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
            client.consumeAsync(params) { result, _ ->
                inFlight.remove(purchase.purchaseToken)
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _tipsReceived.value += 1
                    report("tip", purchase)
                }
            }
            return
        }
        if (purchase.ownsStoriesPass()) {
            setStoriesPass(true)
            if (!purchase.isAcknowledged && inFlight.add(purchase.purchaseToken)) {
                val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
                client.acknowledgePurchase(params) { result ->
                    inFlight.remove(purchase.purchaseToken)
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) report("stories_pass", purchase)
                }
            }
            return
        }
        if (!purchase.ownsRemoveAds()) return
        setAdsRemoved(true)
        // Purchases not acknowledged within 3 days are refunded automatically by Play.
        if (!purchase.isAcknowledged) {
            if (!inFlight.add(purchase.purchaseToken)) return
            val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
            client.acknowledgePurchase(params) { result ->
                inFlight.remove(purchase.purchaseToken)
                if (result.responseCode == BillingClient.BillingResponseCode.OK) report("pro", purchase)
            }
        } else {
            // Already acknowledged but never reported here: a Play promo code redeemed while the app was
            // closed (hook spec v1.3, the Car Hub gift), or a purchase made before this build. The server
            // ignores repeats, so sending one twice is harmless.
            report("pro", purchase)
        }
    }

    /** Tells the Ampli Owner app about a completed purchase (skipped when no server is set). */
    @Synchronized
    private fun report(kind: String, purchase: Purchase) {
        val sku = purchase.products.firstOrNull() ?: return
        // Every launch re-checks purchases, so remember which tokens were already queued.
        val sent = prefs.getStringSet(KEY_REPORTED, emptySet()).orEmpty()
        if (purchase.purchaseToken in sent) return
        com.vikash.mp3player.data.OwnerHooks.purchase(kind, sku, purchase.purchaseToken)
        if (com.vikash.mp3player.data.OwnerHooks.enabled) {
            prefs.edit().putStringSet(KEY_REPORTED, (sent + purchase.purchaseToken).toList().takeLast(50).toSet()).apply()
        }
    }

    private fun Purchase.ownsRemoveAds() =
        purchaseState == Purchase.PurchaseState.PURCHASED &&
            BuildConfig.REMOVE_ADS_PRODUCT_ID in products

    private fun Purchase.ownsStoriesPass() =
        purchaseState == Purchase.PurchaseState.PURCHASED && STORIES_PASS_ID in products

    private fun Purchase.isTip() =
        purchaseState == Purchase.PurchaseState.PURCHASED && products.any { it in TIP_IDS }

    /** Sideloaded test builds can't reach Play Billing, so they get a switch to try Pro. */
    fun setTestPro(value: Boolean) {
        if (BuildConfig.DEBUG) {
            prefs.edit().putBoolean(KEY_TEST_PRO, value).apply()
            setAdsRemoved(value || testerPro)
            setStoriesPass(value || testerPro)
        }
    }

    private fun setAdsRemoved(value: Boolean) {
        _adsRemoved.value = value
        prefs.edit().putBoolean(KEY_ADS_REMOVED, value).apply()
    }

    private fun setStoriesPass(value: Boolean) {
        _storiesPass.value = value
        prefs.edit().putBoolean(KEY_STORIES_PASS, value).apply()
    }

    companion object {
        /** Monthly subscription in Play Console (Monetize > Subscriptions), ₹49 a month. */
        const val STORIES_PASS_ID = "ampli_stories_pass"
        private const val KEY_STORIES_PASS = "stories_pass"
        private const val KEY_ADS_REMOVED = "ads_removed"
        private const val KEY_REPORTED = "reported_tokens"
        private const val KEY_TEST_PRO = "test_pro"
        val TIP_IDS = listOf(BuildConfig.TIP_SMALL_ID, BuildConfig.TIP_LARGE_ID, BuildConfig.TIP_XL_ID)
    }
}

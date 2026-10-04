package com.alexchurkin.truckremote.billing

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.alexchurkin.truckremote.analytics.Analytics
import com.alexchurkin.truckremote.settings.AppSettings
import com.alexchurkin.truckremote.util.logD
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
import com.android.billingclient.api.QueryProductDetailsResult
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

enum class BillingEvent {
    // Purchased right now
    Purchased,

    // Was purchased before (e.g. on another device)
    Restored,

    // Refunded
    Returned,
    Cancelled,
}

/**
 * Handles the only in-app product (ads removal).
 * Connection is kept while the observed screen (settings) is alive,
 * or opened on startup when a purchase still has to be acknowledged.
 */
class BillingManager(context: Context, private val settings: AppSettings) :
    DefaultLifecycleObserver,
    BillingClientStateListener,
    PurchasesUpdatedListener {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private var billingClient: BillingClient? = null
    private var reconnectDelayMs = RECONNECT_START_MS

    @Volatile
    private var adsOffDetails: ProductDetails? = null

    private val _adsRemoved = MutableStateFlow(settings.adsRemoved)
    val adsRemoved: StateFlow<Boolean> = _adsRemoved.asStateFlow()

    private val _events = MutableSharedFlow<BillingEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<BillingEvent> = _events.asSharedFlow()

    init {
        if (!settings.purchasesAcknowledged) {
            logD("* Starting billing connection (purpose: acknowledgement)")
            connect()
        }
    }

    override fun onCreate(owner: LifecycleOwner) = connect()

    override fun onResume(owner: LifecycleOwner) = queryPurchases()

    override fun onDestroy(owner: LifecycleOwner) {
        mainHandler.removeCallbacksAndMessages(null)
        billingClient?.endConnection()
        billingClient = null
        logD("* Billing connection closed")
    }

    fun launchPurchaseFlow(activity: Activity) {
        val details = adsOffDetails
        val client = billingClient
        if (details == null || client == null) {
            logD("* Can't launch purchase flow: product details aren't loaded")
            return
        }
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()
        client.launchBillingFlow(activity, flowParams)
        settings.purchasesAcknowledged = false
    }

    private fun connect() {
        if (billingClient != null) return
        billingClient = BillingClient.newBuilder(appContext)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .setListener(this)
            .build()
            .also { it.startConnection(this) }
    }

    override fun onBillingServiceDisconnected() {
        logD("* Billing was disconnected. Retrying...")
        retryConnection()
    }

    override fun onBillingSetupFinished(result: BillingResult) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            logD("* Billing connection established")
            reconnectDelayMs = RECONNECT_START_MS
            queryProductDetails()
            queryPurchases()
        } else {
            logD("* Billing is not connected: ${result.debugMessage}. Retrying...")
            retryConnection()
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> processPurchases(purchases.orEmpty(), isLiveUpdate = true)
            BillingClient.BillingResponseCode.USER_CANCELED -> _events.tryEmit(BillingEvent.Cancelled)
            else -> logD("* Purchase failed: ${result.debugMessage}")
        }
    }

    private fun queryProductDetails() {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRODUCT_ADS_OFF)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()
        billingClient?.queryProductDetailsAsync(params, ::onProductDetails)
    }

    private fun onProductDetails(result: BillingResult, detailsResult: QueryProductDetailsResult) {
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            logD("* Problem getting product details: ${result.debugMessage}")
            return
        }
        adsOffDetails = detailsResult.productDetailsList.firstOrNull { it.productId == PRODUCT_ADS_OFF }
        if (adsOffDetails == null) logD("* Product isn't found, check Google Play Console")
    }

    private fun queryPurchases() {
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        billingClient?.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                processPurchases(purchases, isLiveUpdate = false)
            } else {
                logD("* Problem getting purchases: ${result.debugMessage}")
            }
        }
    }

    private fun processPurchases(purchases: List<Purchase>, isLiveUpdate: Boolean) {
        val adsOffPurchase = purchases.firstOrNull { PRODUCT_ADS_OFF in it.products }
        val purchased = adsOffPurchase?.purchaseState == Purchase.PurchaseState.PURCHASED
        val wasPurchased = settings.adsRemoved

        if (purchased != wasPurchased) {
            settings.adsRemoved = purchased
            _adsRemoved.value = purchased
            val event = when {
                !purchased -> BillingEvent.Returned
                isLiveUpdate -> BillingEvent.Purchased
                else -> BillingEvent.Restored
            }
            _events.tryEmit(event)
            Analytics.report(Analytics.EVENT_ADS_REMOVED, mapOf("result" to event.name))
        }

        if (adsOffPurchase != null && purchased && !adsOffPurchase.isAcknowledged) {
            acknowledge(adsOffPurchase)
        } else {
            settings.purchasesAcknowledged = true
        }
    }

    private fun acknowledge(purchase: Purchase) {
        settings.purchasesAcknowledged = false
        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
        billingClient?.acknowledgePurchase(params) { result ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                logD("* Purchase was acknowledged")
                settings.purchasesAcknowledged = true
            } else {
                logD("* Acknowledgement failed: ${result.debugMessage}")
            }
        }
    }

    private fun retryConnection() {
        mainHandler.postDelayed({ billingClient?.startConnection(this) }, reconnectDelayMs)
        reconnectDelayMs = (reconnectDelayMs * 2).coerceAtMost(RECONNECT_MAX_MS)
    }

    private companion object {
        const val PRODUCT_ADS_OFF = "add_off"
        const val RECONNECT_START_MS = 1000L
        const val RECONNECT_MAX_MS = 10 * 60 * 1000L
    }
}

package com.alexchurkin.truckremote.billing

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.alexchurkin.truckremote.analytics.Analytics
import com.alexchurkin.truckremote.settings.AppSettings
import com.alexchurkin.truckremote.util.logD
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

enum class BillingEvent {
    // Was purchased before (e.g. on another device or before reinstalling)
    Restored,

    // Refunded
    Returned,

    // Restoring was asked, but the account has no purchase
    NotFound,

    // Restoring was asked, but Google Play isn't available
    Failed,
}

/**
 * Ads removal can't be bought anymore, but earlier purchases are kept:
 * they are checked on every app start (silently) and on the user's request (with a result message).
 * The connection to Google Play is closed after every check.
 */
class BillingManager(context: Context, private val settings: AppSettings) :
    BillingClientStateListener,
    PurchasesUpdatedListener {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private var billingClient: BillingClient? = null
    private var checking = false
    private var userRequested = false
    private var acknowledging = false
    private var attempt = 0

    private val _adsRemoved = MutableStateFlow(settings.adsRemoved)
    val adsRemoved: StateFlow<Boolean> = _adsRemoved.asStateFlow()

    private val _events = MutableSharedFlow<BillingEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<BillingEvent> = _events.asSharedFlow()

    // Must be called on the main thread
    fun checkPurchases() = startCheck(byUser = false)

    // Must be called on the main thread
    fun restorePurchase() = startCheck(byUser = true)

    private fun startCheck(byUser: Boolean) {
        userRequested = userRequested || byUser
        if (checking) return
        checking = true
        attempt = 0
        connect()
    }

    private fun connect() {
        val client = billingClient ?: BillingClient.newBuilder(appContext)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .setListener(this)
            .build()
            .also { billingClient = it }
        if (client.isReady) queryPurchases() else client.startConnection(this)
    }

    override fun onBillingSetupFinished(result: BillingResult) {
        mainHandler.post {
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                logD("* Billing connection established")
                queryPurchases()
            } else {
                logD("* Billing is not connected: ${result.debugMessage}")
                // No Google Play (or it is too old): retrying won't help
                retryOrFail(canRetry = result.responseCode != BillingClient.BillingResponseCode.BILLING_UNAVAILABLE)
            }
        }
    }

    override fun onBillingServiceDisconnected() {
        mainHandler.post {
            logD("* Billing was disconnected")
            retryOrFail()
        }
    }

    // Purchases can't be started anymore, but a pending one may be completed
    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        mainHandler.post {
            if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                applyPurchases(purchases, reportNotFound = false)
            }
        }
    }

    private fun queryPurchases() {
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        billingClient?.queryPurchasesAsync(params) { result, purchases ->
            mainHandler.post {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    applyPurchases(purchases, reportNotFound = userRequested)
                    finishCheck()
                } else {
                    logD("* Problem getting purchases: ${result.debugMessage}")
                    retryOrFail()
                }
            }
        }
    }

    private fun applyPurchases(purchases: List<Purchase>, reportNotFound: Boolean) {
        val purchase = purchases.firstOrNull { PRODUCT_ADS_OFF in it.products }
        val owned = purchase?.purchaseState == Purchase.PurchaseState.PURCHASED

        val changed = owned != settings.adsRemoved
        if (changed) {
            settings.adsRemoved = owned
            _adsRemoved.value = owned
        }
        resultEvent(owned, changed, reportNotFound)?.let { event ->
            _events.tryEmit(event)
            Analytics.report(Analytics.EVENT_ADS_REMOVED, mapOf("result" to event.name))
        }

        if (purchase != null && owned && !purchase.isAcknowledged) acknowledge(purchase)
    }

    // Unacknowledged purchases are refunded by Google Play after 3 days
    private fun acknowledge(purchase: Purchase) {
        acknowledging = true
        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
        billingClient?.acknowledgePurchase(params) { result ->
            mainHandler.post {
                logD("* Acknowledgement: ${result.responseCode} ${result.debugMessage}")
                acknowledging = false
                if (!checking) disconnect()
            }
        }
    }

    private fun retryOrFail(canRetry: Boolean = true) {
        if (!checking) return
        if (canRetry && ++attempt < MAX_ATTEMPTS) {
            mainHandler.postDelayed({ if (checking) connect() }, RETRY_DELAY_MS * attempt)
            return
        }
        if (userRequested) _events.tryEmit(BillingEvent.Failed)
        finishCheck()
    }

    private fun finishCheck() {
        checking = false
        userRequested = false
        if (!acknowledging) disconnect()
    }

    private fun disconnect() {
        billingClient?.endConnection()
        billingClient = null
        logD("* Billing connection closed")
    }

    companion object {
        private const val PRODUCT_ADS_OFF = "add_off"
        private const val MAX_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 2000L

        /**
         * Message for the user after a check: changes are always reported,
         * "not found" only when the user asked to restore.
         */
        internal fun resultEvent(owned: Boolean, changed: Boolean, reportNotFound: Boolean): BillingEvent? = when {
            changed && owned -> BillingEvent.Restored
            changed -> BillingEvent.Returned
            reportNotFound && owned -> BillingEvent.Restored
            reportNotFound -> BillingEvent.NotFound
            else -> null
        }
    }
}

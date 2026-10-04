package com.alexchurkin.truckremote

import android.app.Application
import android.content.Context
import com.alexchurkin.truckremote.ads.AdManager
import com.alexchurkin.truckremote.analytics.Analytics
import com.alexchurkin.truckremote.billing.BillingManager
import com.alexchurkin.truckremote.settings.AppSettings
import com.alexchurkin.truckremote.util.Toaster

class TruckRemoteApp : Application() {

    lateinit var settings: AppSettings
        private set

    lateinit var billing: BillingManager
        private set

    lateinit var ads: AdManager
        private set

    override fun onCreate() {
        super.onCreate()
        Analytics.initialize(this)
        settings = AppSettings.create(this)
        Toaster.initialize(this)
        billing = BillingManager(this, settings)
        // Restores ads removal bought earlier (e.g. after reinstalling) before an ad is shown
        billing.checkPurchases()
        ads = AdManager(settings)
        ads.initialize(this)
    }
}

val Context.app: TruckRemoteApp
    get() = applicationContext as TruckRemoteApp

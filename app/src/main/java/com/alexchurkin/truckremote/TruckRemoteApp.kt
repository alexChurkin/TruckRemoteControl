package com.alexchurkin.truckremote

import android.app.Application
import android.content.Context
import com.alexchurkin.truckremote.di.AppContainer

class TruckRemoteApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Restores ads removal bought earlier (e.g. after reinstalling) before an ad is shown
        container.billing.checkPurchases()
        container.ads.initialize(this)
    }
}

val Context.app: TruckRemoteApp
    get() = applicationContext as TruckRemoteApp

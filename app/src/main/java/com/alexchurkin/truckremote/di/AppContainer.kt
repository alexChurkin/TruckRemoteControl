package com.alexchurkin.truckremote.di

import android.content.Context
import com.alexchurkin.truckremote.data.ads.AdManager
import com.alexchurkin.truckremote.data.analytics.Analytics
import com.alexchurkin.truckremote.data.analytics.AppMetricaAnalytics
import com.alexchurkin.truckremote.data.billing.BillingManager
import com.alexchurkin.truckremote.data.controller.ControllerRepository
import com.alexchurkin.truckremote.data.controller.UdpControllerRepository
import com.alexchurkin.truckremote.data.device.AndroidHaptics
import com.alexchurkin.truckremote.data.device.AndroidWifiStatus
import com.alexchurkin.truckremote.data.device.Haptics
import com.alexchurkin.truckremote.data.device.LowLatencyWifiLock
import com.alexchurkin.truckremote.data.device.WifiStatus
import com.alexchurkin.truckremote.data.region.DataRegion
import com.alexchurkin.truckremote.data.sensor.AndroidTiltSensor
import com.alexchurkin.truckremote.data.sensor.TiltSensor
import com.alexchurkin.truckremote.data.settings.AppSettings

/**
 * Manual dependency injection: the app-wide objects are created here once,
 * view models get them through their factories (so they can be replaced in tests).
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    // What the country of the device allows to send: decided once at the start
    val region = DataRegion.detect(appContext)
    val analytics: Analytics = AppMetricaAnalytics(appContext, allowed = region.analyticsAllowed)
    val settings: AppSettings = AppSettings.create(appContext)
    val billing = BillingManager(appContext, settings, analytics)
    val ads = AdManager(settings, allowed = region.adsAllowed)
    val tiltSensor: TiltSensor = AndroidTiltSensor(appContext)
    val haptics: Haptics = AndroidHaptics(appContext)
    val wifiStatus: WifiStatus = AndroidWifiStatus(appContext)

    // Every controller screen has its own connection, it is closed together with the screen
    fun createControllerRepository(): ControllerRepository = UdpControllerRepository(LowLatencyWifiLock(appContext))
}

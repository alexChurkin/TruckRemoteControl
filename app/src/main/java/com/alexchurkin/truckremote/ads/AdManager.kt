package com.alexchurkin.truckremote.ads

import android.app.Activity
import android.content.Context
import com.alexchurkin.truckremote.BuildConfig
import com.alexchurkin.truckremote.settings.AppSettings
import com.alexchurkin.truckremote.util.logD
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

// Shows at most one interstitial ad per app session
class AdManager(private val settings: AppSettings) {

    private var initialized = false
    private var initializing = false
    private var preloadedAd: InterstitialAd? = null
    private var adShown = false
    private var showingNow = false

    fun initialize(context: Context) {
        if (settings.adsRemoved || initialized || initializing) return
        logD("> AdManager is initializing")
        initializing = true
        val appContext = context.applicationContext
        MobileAds.initialize(appContext) {
            logD("> AdManager was initialized")
            initializing = false
            initialized = true
            preload(appContext)
        }
    }

    private fun preload(context: Context) {
        InterstitialAd.load(
            context,
            BuildConfig.INTERSTITIAL_AD_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    preloadedAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    logD("> Ad failed to load: ${error.message}")
                }
            },
        )
    }

    // Does nothing if ads are removed, the ad isn't loaded yet or was already shown
    fun tryShowFullscreenAd(activity: Activity) {
        val ad = preloadedAd
        if (settings.adsRemoved || adShown || showingNow || ad == null) return
        showingNow = true
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                adShown = true
                showingNow = false
                preloadedAd = null
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                showingNow = false
            }
        }
        ad.show(activity)
    }
}

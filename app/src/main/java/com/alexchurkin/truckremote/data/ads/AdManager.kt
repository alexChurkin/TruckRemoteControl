package com.alexchurkin.truckremote.data.ads

import android.app.Activity
import android.content.Context
import com.alexchurkin.truckremote.BuildConfig
import com.alexchurkin.truckremote.data.settings.AppSettings
import com.alexchurkin.truckremote.util.logD
import com.yandex.mobile.ads.common.AdError
import com.yandex.mobile.ads.common.AdRequest
import com.yandex.mobile.ads.common.AdRequestError
import com.yandex.mobile.ads.common.ImpressionData
import com.yandex.mobile.ads.common.YandexAds
import com.yandex.mobile.ads.interstitial.InterstitialAd
import com.yandex.mobile.ads.interstitial.InterstitialAdEventListener
import com.yandex.mobile.ads.interstitial.InterstitialAdLoadListener
import com.yandex.mobile.ads.interstitial.InterstitialAdLoader

// Yandex Ads; shows at most one interstitial ad per app session
class AdManager(private val settings: AppSettings) {

    private var initialized = false
    private var initializing = false
    private var loader: InterstitialAdLoader? = null
    private var preloadedAd: InterstitialAd? = null
    private var adShown = false
    private var showingNow = false

    fun initialize(context: Context) {
        if (settings.adsRemoved || initialized || initializing) return
        logD("> AdManager is initializing")
        initializing = true
        val appContext = context.applicationContext
        YandexAds.enableLogging(BuildConfig.USE_LOG)
        YandexAds.initialize(appContext) {
            logD("> AdManager was initialized")
            initializing = false
            initialized = true
            preload(appContext)
        }
    }

    private fun preload(context: Context) {
        val adLoader = loader ?: InterstitialAdLoader(context).also { loader = it }
        adLoader.loadAd(
            AdRequest.Builder(BuildConfig.INTERSTITIAL_AD_ID).build(),
            object : InterstitialAdLoadListener {
                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    preloadedAd = interstitialAd
                }

                override fun onAdFailedToLoad(error: AdRequestError) {
                    logD("> Ad failed to load: ${error.code} ${error.description}")
                }
            },
        )
    }

    // Does nothing if ads are removed, the ad isn't loaded yet or was already shown
    fun tryShowFullscreenAd(activity: Activity) {
        val ad = preloadedAd
        if (settings.adsRemoved || adShown || showingNow || ad == null) return
        showingNow = true
        ad.setAdEventListener(
            object : InterstitialAdEventListener {
                override fun onAdShown() {
                    adShown = true
                    showingNow = false
                    preloadedAd = null
                }

                override fun onAdFailedToShow(adError: AdError) {
                    logD("> Ad failed to show: ${adError.description}")
                    showingNow = false
                }

                override fun onAdDismissed() = Unit

                override fun onAdClicked() = Unit

                override fun onAdImpression(impressionData: ImpressionData?) = Unit
            },
        )
        ad.show(activity)
    }
}

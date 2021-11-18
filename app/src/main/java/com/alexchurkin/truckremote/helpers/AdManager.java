package com.alexchurkin.truckremote.helpers;

import static com.alexchurkin.truckremote.helpers.BillingMan.PREF_AD_OFF;
import static com.alexchurkin.truckremote.helpers.LogMan.logD;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.NonNull;

import com.alexchurkin.truckremote.BuildConfig;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;

public class AdManager {

    private static volatile boolean isSdkInitialized = false;
    private static volatile boolean isInitializingNow = false;

    private static InterstitialAd preloadedBanner;
    private static boolean wasBannerShown = false;

    private static boolean isTryingToShowNow = false;

    private AdManager() {
    }

    public static void init(@NonNull Context context) {
        boolean adDisabled = Prefs.getBoolean(PREF_AD_OFF, false);

        if (!adDisabled && !isSdkInitialized && !isInitializingNow) {
            logD("> AdManager is initializing");
            isInitializingNow = true;
            MobileAds.initialize(context, initializationStatus -> {
                logD("> AdManager was initialized");
                isInitializingNow = false;
                isSdkInitialized = true;
                preloadFullscreenAd(context);
            });
        }
    }

    private static void preloadFullscreenAd(@NonNull Context context) {
        AdRequest adRequest = new AdRequest.Builder().build();
        InterstitialAd.load(context, BuildConfig.INTERSTITIAL_AD_ID, adRequest, new InterstitialAdLoadCallback() {
            @Override
            public void onAdLoaded(@NonNull InterstitialAd interstitialAd) {
                preloadedBanner = interstitialAd;
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {

            }
        });
    }

    //Trying to show fullscreen ad (if SDK is not initialized, if ad wasn't loaded,
    //if ad was shown before during this session or if ad is trying to show now,
    //it won't happen)
    public static void tryShowFullscreenAd(@NonNull Activity activity) {
        if (isTryingToShowNow) return;
        isTryingToShowNow = true;

        if (isSdkInitialized && preloadedBanner != null && !wasBannerShown) {
            preloadedBanner.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdShowedFullScreenContent() {
                    wasBannerShown = true;
                    isTryingToShowNow = false;
                }

                @Override
                public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                    isTryingToShowNow = false;
                }
            });
            preloadedBanner.show(activity);
        }
    }
}

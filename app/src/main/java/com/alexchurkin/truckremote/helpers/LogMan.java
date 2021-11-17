package com.alexchurkin.truckremote.helpers;

import android.os.Looper;
import android.util.Log;

import com.alexchurkin.truckremote.BuildConfig;

public class LogMan {
    private static final String LTAG = "TRem";

    public static void logThreadD() {
        logD("Is UI thread: " + (Looper.myLooper() == Looper.getMainLooper()));
    }

    public static void logD(String msg) {
        if (BuildConfig.USE_LOG)
            Log.d(LTAG, msg);
    }

    public static void logE(String msg) {
        if (BuildConfig.USE_LOG)
            Log.e(LTAG, msg);
    }
}
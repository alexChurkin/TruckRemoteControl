package com.alexchurkin.truckremote.util

import android.os.Looper
import android.util.Log
import com.alexchurkin.truckremote.BuildConfig

private const val TAG = "TRem"

fun logD(message: String) {
    if (BuildConfig.USE_LOG) Log.d(TAG, message)
}

fun logE(message: String, error: Throwable? = null) {
    if (BuildConfig.USE_LOG) Log.e(TAG, message, error)
}

fun logThread() {
    logD("Is UI thread: ${Looper.myLooper() == Looper.getMainLooper()}")
}

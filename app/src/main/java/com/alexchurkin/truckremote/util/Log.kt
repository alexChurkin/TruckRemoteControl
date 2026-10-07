package com.alexchurkin.truckremote.util

import android.util.Log
import com.alexchurkin.truckremote.BuildConfig

private const val TAG = "TRem"

fun logD(message: String) {
    if (BuildConfig.USE_LOG) Log.d(TAG, message)
}

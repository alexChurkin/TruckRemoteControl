package com.alexchurkin.truckremote.util

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes

// Shows only the latest toast, so quick events don't queue up
object Toaster {
    private lateinit var appContext: Context
    private var lastToast: Toast? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun show(@StringRes resId: Int) = show(appContext.getString(resId))

    fun show(text: String) {
        lastToast?.cancel()
        lastToast = Toast.makeText(appContext, text, Toast.LENGTH_SHORT).also { it.show() }
    }
}

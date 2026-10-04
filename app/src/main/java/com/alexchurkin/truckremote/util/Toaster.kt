package com.alexchurkin.truckremote.util

import android.content.Context
import android.content.res.Configuration
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.ConfigurationCompat

// Shows only the latest toast, so quick events don't queue up
object Toaster {
    private lateinit var appContext: Context
    private var lastToast: Toast? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun show(@StringRes resId: Int) = show(localizedContext().getString(resId))

    fun show(text: String) {
        lastToast?.cancel()
        lastToast = Toast.makeText(appContext, text, Toast.LENGTH_SHORT).also { it.show() }
    }

    // On Android 12 and older the application context doesn't get the language chosen in the app
    private fun localizedContext(): Context {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return appContext
        val configuration = Configuration(appContext.resources.configuration)
        ConfigurationCompat.setLocales(configuration, locales)
        return appContext.createConfigurationContext(configuration)
    }
}

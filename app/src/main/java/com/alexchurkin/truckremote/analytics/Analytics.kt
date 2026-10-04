package com.alexchurkin.truckremote.analytics

import android.content.Context
import com.alexchurkin.truckremote.BuildConfig
import com.alexchurkin.truckremote.util.logD
import io.appmetrica.analytics.AppMetrica
import io.appmetrica.analytics.AppMetricaConfig

/**
 * AppMetrica: sessions, crashes and a few events.
 * Without the API key (debug builds, builds without secrets) nothing is collected.
 */
object Analytics {

    @Volatile
    private var enabled = false

    fun initialize(context: Context) {
        val apiKey = BuildConfig.APPMETRICA_API_KEY
        if (apiKey.isEmpty()) {
            logD("AppMetrica is disabled: no API key")
            return
        }
        val config = AppMetricaConfig.newConfigBuilder(apiKey)
            .withCrashReporting(true)
            .withLocationTracking(false)
            .build()
        AppMetrica.activate(context.applicationContext, config)
        enabled = true
    }

    fun report(event: String, params: Map<String, Any> = emptyMap()) {
        logD("Analytics event: $event $params")
        if (!enabled) return
        if (params.isEmpty()) AppMetrica.reportEvent(event) else AppMetrica.reportEvent(event, params)
    }

    const val EVENT_SERVER_CONNECTED = "server_connected"
    const val EVENT_ADS_REMOVED = "ads_removed"
}

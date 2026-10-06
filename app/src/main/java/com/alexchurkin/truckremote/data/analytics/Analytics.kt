package com.alexchurkin.truckremote.data.analytics

import android.content.Context
import com.alexchurkin.truckremote.BuildConfig
import com.alexchurkin.truckremote.util.logD
import io.appmetrica.analytics.AppMetrica
import io.appmetrica.analytics.AppMetricaConfig

interface Analytics {
    fun report(event: String, params: Map<String, Any> = emptyMap())

    companion object {
        const val EVENT_SERVER_CONNECTED = "server_connected"
        const val EVENT_ADS_REMOVED = "ads_removed"
    }
}

/**
 * AppMetrica: sessions, crashes and a few events.
 * Without the API key (debug builds, builds without secrets) nothing is collected, and nothing is where
 * it isn't [allowed] (see DataRegion): AppMetrica isn't even started there.
 */
class AppMetricaAnalytics(context: Context, allowed: Boolean = true) : Analytics {

    private val enabled: Boolean = allowed && BuildConfig.APPMETRICA_API_KEY.isNotEmpty()

    init {
        if (enabled) {
            val config = AppMetricaConfig.newConfigBuilder(BuildConfig.APPMETRICA_API_KEY)
                .withCrashReporting(true)
                .withLocationTracking(false)
                .build()
            AppMetrica.activate(context.applicationContext, config)
        } else {
            logD("AppMetrica is disabled: no API key, or not in this country")
        }
    }

    override fun report(event: String, params: Map<String, Any>) {
        logD("Analytics event: $event $params")
        if (!enabled) return
        if (params.isEmpty()) AppMetrica.reportEvent(event) else AppMetrica.reportEvent(event, params)
    }
}

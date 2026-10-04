package com.alexchurkin.truckremote.data.device

import android.content.Context
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat

interface WifiStatus {
    val isEnabled: Boolean

    // Signal strength in dBm
    val rssi: Int
}

class AndroidWifiStatus(context: Context) : WifiStatus {

    private val wifiManager = ContextCompat.getSystemService(context.applicationContext, WifiManager::class.java)

    override val isEnabled: Boolean
        get() = wifiManager?.isWifiEnabled == true

    override val rssi: Int
        @Suppress("DEPRECATION")
        get() = wifiManager?.connectionInfo?.rssi ?: 0
}

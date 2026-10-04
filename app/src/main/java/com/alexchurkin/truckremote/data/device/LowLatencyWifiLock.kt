package com.alexchurkin.truckremote.data.device

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.alexchurkin.truckremote.util.logD

/**
 * Keeps Wi-Fi out of the power save mode while the controller is used:
 * power save delays incoming packets by tens of milliseconds and makes the latency uneven.
 * Low latency mode (Android 10+) works only while the app is in foreground with the screen on.
 */
class LowLatencyWifiLock(context: Context) {

    private val lock: WifiManager.WifiLock? =
        ContextCompat.getSystemService(context.applicationContext, WifiManager::class.java)
            ?.createWifiLock(lockMode(), "TruckRemote:controller")
            ?.apply { setReferenceCounted(false) }

    fun acquire() {
        if (lock?.isHeld == false) {
            lock.acquire()
            logD("Wi-Fi lock acquired")
        }
    }

    fun release() {
        if (lock?.isHeld == true) {
            lock.release()
            logD("Wi-Fi lock released")
        }
    }

    private companion object {
        fun lockMode() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            WifiManager.WIFI_MODE_FULL_LOW_LATENCY
        } else {
            @Suppress("DEPRECATION")
            WifiManager.WIFI_MODE_FULL_HIGH_PERF
        }
    }
}

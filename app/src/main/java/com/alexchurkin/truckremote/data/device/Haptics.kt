package com.alexchurkin.truckremote.data.device

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.ContextCompat

// Vibration of the phone (force feedback of the game)
interface Haptics {
    fun vibrate(durationMs: Long)
}

class AndroidHaptics(context: Context) : Haptics {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.getSystemService(context, VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        ContextCompat.getSystemService(context, Vibrator::class.java)
    }

    override fun vibrate(durationMs: Long) {
        val vibrator = vibrator?.takeIf { it.hasVibrator() } ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs)
        }
    }
}

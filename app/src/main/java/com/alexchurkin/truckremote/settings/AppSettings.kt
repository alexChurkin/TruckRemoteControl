package com.alexchurkin.truckremote.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

enum class PedalMode(val prefValue: String) {
    // Keyboard keys, pedal is always fully pressed
    Digital("digital"),

    // vJoy axes, press force is set by dragging
    Analog("analog"),
    ;

    companion object {
        fun fromPrefValue(value: String?) = entries.firstOrNull { it.prefValue == value } ?: Digital
    }
}

/**
 * Typed access to the app preferences.
 * Keys and value types are the same as in previous versions, so user settings are kept after update.
 */
class AppSettings(private val prefs: SharedPreferences) {

    var serverPort: Int
        get() = prefs.getString(KEY_PORT, null)?.toIntOrNull()?.takeIf { it in PORT_RANGE } ?: DEFAULT_PORT
        set(value) = prefs.edit { putString(KEY_PORT, value.toString()) }

    var useSpecifiedServer: Boolean
        get() = prefs.getBoolean(KEY_USE_SPECIFIED_SERVER, false)
        set(value) = prefs.edit { putBoolean(KEY_USE_SPECIFIED_SERVER, value) }

    var specifiedServerIp: String
        get() = prefs.getString(KEY_SPECIFIED_IP, null).orEmpty()
        set(value) = prefs.edit { putString(KEY_SPECIFIED_IP, value) }

    var forceFeedback: Boolean
        get() = prefs.getBoolean(KEY_FORCE_FEEDBACK, false)
        set(value) = prefs.edit { putBoolean(KEY_FORCE_FEEDBACK, value) }

    var pneumaticHorn: Boolean
        get() = prefs.getBoolean(KEY_PNEUMATIC_HORN, false)
        set(value) = prefs.edit { putBoolean(KEY_PNEUMATIC_HORN, value) }

    // Previous versions had only a switch, its dead zone was about 6 degrees
    var steeringDeadZone: Int
        get() = prefs.getInt(
            KEY_STEERING_DEAD_ZONE,
            if (prefs.getBoolean(KEY_DEAD_ZONE, false)) LEGACY_DEAD_ZONE else 0,
        )
            .coerceIn(STEERING_DEAD_ZONE_RANGE)
        set(value) = prefs.edit { putInt(KEY_STEERING_DEAD_ZONE, value) }

    var steeringMaxAngle: Int
        get() = prefs.getInt(KEY_STEERING_MAX_ANGLE, STEERING_MAX_ANGLE_RANGE.last)
            .coerceIn(STEERING_MAX_ANGLE_RANGE)
        set(value) = prefs.edit { putInt(KEY_STEERING_MAX_ANGLE, value) }

    var steeringExponent: Float
        get() = prefs.getFloat(KEY_STEERING_EXPONENT, STEERING_EXPONENT_RANGE.start)
            .coerceIn(STEERING_EXPONENT_RANGE)
        set(value) = prefs.edit { putFloat(KEY_STEERING_EXPONENT, value) }

    var pedalMode: PedalMode
        get() = PedalMode.fromPrefValue(prefs.getString(KEY_PEDAL_MODE, null))
        set(value) = prefs.edit { putString(KEY_PEDAL_MODE, value.prefValue) }

    var throttleLock: Boolean
        get() = prefs.getBoolean(KEY_THROTTLE_LOCK, true)
        set(value) = prefs.edit { putBoolean(KEY_THROTTLE_LOCK, value) }

    var calibrationOffset: Float
        get() = prefs.getFloat(KEY_CALIBRATION_OFFSET, 0f)
        set(value) = prefs.edit { putFloat(KEY_CALIBRATION_OFFSET, value) }

    var guideShown: Boolean
        get() = prefs.getBoolean(KEY_GUIDE_SHOWN, false)
        set(value) = prefs.edit { putBoolean(KEY_GUIDE_SHOWN, value) }

    var lastShownReleaseNotes: Int
        get() = prefs.getInt(KEY_LAST_RELEASE_NOTES, 0)
        set(value) = prefs.edit { putInt(KEY_LAST_RELEASE_NOTES, value) }

    var adsRemoved: Boolean
        get() = prefs.getBoolean(KEY_ADS_REMOVED, false)
        set(value) = prefs.edit { putBoolean(KEY_ADS_REMOVED, value) }

    // Emits on every change of any preference
    fun changes(): Flow<Unit> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(Unit) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    companion object {
        const val DEFAULT_PORT = 18250
        val PORT_RANGE = 10000..65535
        val STEERING_DEAD_ZONE_RANGE = 0..15
        val STEERING_MAX_ANGLE_RANGE = 20..90
        val STEERING_EXPONENT_RANGE = 1f..3f

        private const val KEY_PORT = "serverPort"
        private const val KEY_USE_SPECIFIED_SERVER = "defaultServer"
        private const val KEY_SPECIFIED_IP = "serverIP"
        private const val KEY_FORCE_FEEDBACK = "useFFB"
        private const val KEY_PNEUMATIC_HORN = "pneumaticSignal"
        private const val KEY_DEAD_ZONE = "deadzone"
        private const val LEGACY_DEAD_ZONE = 6
        private const val KEY_STEERING_DEAD_ZONE = "steeringDeadZone"
        private const val KEY_STEERING_MAX_ANGLE = "steeringMaxAngle"
        private const val KEY_STEERING_EXPONENT = "steeringExponent"
        private const val KEY_PEDAL_MODE = "pedalMode"
        private const val KEY_THROTTLE_LOCK = "throttleLock"
        private const val KEY_CALIBRATION_OFFSET = "calibrationOffset"
        private const val KEY_GUIDE_SHOWN = "guideShowed"
        private const val KEY_LAST_RELEASE_NOTES = "releaseVersionText"
        private const val KEY_ADS_REMOVED = "prefadsetting"

        // The same file as PreferenceManager.getDefaultSharedPreferences() used before
        fun create(context: Context) = AppSettings(
            context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE),
        )
    }
}

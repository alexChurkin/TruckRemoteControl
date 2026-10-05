package com.alexchurkin.truckremote.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.alexchurkin.truckremote.domain.SteeringCurve
import com.alexchurkin.truckremote.domain.SteeringProcessor
import kotlin.math.asin
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

    // The server found by the search last time: it is asked directly during the next search
    var lastServerIp: String?
        get() = prefs.getString(KEY_LAST_SERVER_IP, null)
        set(value) = prefs.edit { putString(KEY_LAST_SERVER_IP, value) }

    var forceFeedback: Boolean
        get() = prefs.getBoolean(KEY_FORCE_FEEDBACK, false)
        set(value) = prefs.edit { putBoolean(KEY_FORCE_FEEDBACK, value) }

    var pneumaticHorn: Boolean
        get() = prefs.getBoolean(KEY_PNEUMATIC_HORN, false)
        set(value) = prefs.edit { putBoolean(KEY_PNEUMATIC_HORN, value) }

    // Speed, cruise control and other instruments in the middle of the controller screen
    var showDashboard: Boolean
        get() = prefs.getBoolean(KEY_SHOW_DASHBOARD, true)
        set(value) = prefs.edit { putBoolean(KEY_SHOW_DASHBOARD, value) }

    // Previous versions had only a switch, its dead zone was about 6 degrees
    var steeringDeadZone: Int
        get() = prefs.getInt(
            KEY_STEERING_DEAD_ZONE,
            if (prefs.getBoolean(KEY_DEAD_ZONE, false)) LEGACY_DEAD_ZONE else 0,
        )
            .coerceIn(STEERING_DEAD_ZONE_RANGE)
        set(value) = prefs.edit { putInt(KEY_STEERING_DEAD_ZONE, value) }

    var steeringMaxAngle: Int
        get() = prefs.getInt(KEY_STEERING_MAX_ANGLE, DEFAULT_STEERING_MAX_ANGLE)
            .coerceIn(STEERING_MAX_ANGLE_RANGE)
        set(value) = prefs.edit { putInt(KEY_STEERING_MAX_ANGLE, value) }

    var steeringExponent: Float
        get() = prefs.getFloat(KEY_STEERING_EXPONENT, DEFAULT_STEERING_EXPONENT)
            .coerceIn(STEERING_EXPONENT_RANGE)
        set(value) = prefs.edit { putFloat(KEY_STEERING_EXPONENT, value) }

    // See SteeringProcessor.smoothness
    var steeringSmoothness: Int
        get() = prefs.getInt(KEY_STEERING_SMOOTHNESS, SteeringProcessor.DEFAULT_SMOOTHNESS)
            .coerceIn(SteeringProcessor.SMOOTHNESS_RANGE)
        set(value) = prefs.edit { putInt(KEY_STEERING_SMOOTHNESS, value) }

    var actionLayout: ActionLayout
        get() = ActionLayout.decode(prefs.getString(KEY_ACTION_LAYOUT, null))
        set(value) = prefs.edit { putString(KEY_ACTION_LAYOUT, value.encode()) }

    var pedalMode: PedalMode
        get() = PedalMode.fromPrefValue(prefs.getString(KEY_PEDAL_MODE, null))
        set(value) = prefs.edit { putString(KEY_PEDAL_MODE, value.prefValue) }

    var throttleLock: Boolean
        get() = prefs.getBoolean(KEY_THROTTLE_LOCK, false)
        set(value) = prefs.edit { putBoolean(KEY_THROTTLE_LOCK, value) }

    // Degrees added to the steering angle (the straight position chosen by the user).
    // Earlier versions stored the gravity projection (m/s²): it's converted to the angle once
    var calibrationOffset: Float
        get() = when {
            prefs.contains(KEY_CALIBRATION_OFFSET_DEG) -> prefs.getFloat(KEY_CALIBRATION_OFFSET_DEG, 0f)

            prefs.contains(KEY_CALIBRATION_OFFSET) -> {
                val projection = prefs.getFloat(KEY_CALIBRATION_OFFSET, 0f) / SteeringCurve.GRAVITY
                Math.toDegrees(asin(projection.coerceIn(-1f, 1f)).toDouble()).toFloat()
            }

            else -> 0f
        }
        set(value) = prefs.edit {
            putFloat(KEY_CALIBRATION_OFFSET_DEG, value)
            remove(KEY_CALIBRATION_OFFSET)
        }

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

        // A bit smoother near the center than the linear curve of previous versions
        const val DEFAULT_STEERING_EXPONENT = 1.5f

        // The sensitivity is set on the phone since protocol 2 (the server applies it as is):
        // full lock at 75° feels like the previous default sensitivity of the server
        const val DEFAULT_STEERING_MAX_ANGLE = 75

        private const val KEY_PORT = "serverPort"
        private const val KEY_USE_SPECIFIED_SERVER = "defaultServer"
        private const val KEY_SPECIFIED_IP = "serverIP"
        private const val KEY_FORCE_FEEDBACK = "useFFB"
        private const val KEY_LAST_SERVER_IP = "lastServerIp"
        private const val KEY_PNEUMATIC_HORN = "pneumaticSignal"
        private const val KEY_SHOW_DASHBOARD = "showDashboard"
        private const val KEY_DEAD_ZONE = "deadzone"
        private const val LEGACY_DEAD_ZONE = 6
        private const val KEY_STEERING_DEAD_ZONE = "steeringDeadZone"
        private const val KEY_STEERING_MAX_ANGLE = "steeringMaxAngle"
        private const val KEY_STEERING_EXPONENT = "steeringExponent"
        private const val KEY_STEERING_SMOOTHNESS = "steeringSmoothness"
        private const val KEY_ACTION_LAYOUT = "actionLayout"
        private const val KEY_PEDAL_MODE = "pedalMode"
        private const val KEY_THROTTLE_LOCK = "throttleLock"
        private const val KEY_CALIBRATION_OFFSET = "calibrationOffset"
        private const val KEY_CALIBRATION_OFFSET_DEG = "calibrationOffsetDeg"
        private const val KEY_GUIDE_SHOWN = "guideShowed"
        private const val KEY_LAST_RELEASE_NOTES = "releaseVersionText"
        private const val KEY_ADS_REMOVED = "prefadsetting"

        // The same file as PreferenceManager.getDefaultSharedPreferences() used before
        fun create(context: Context) = AppSettings(
            context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE),
        )
    }
}

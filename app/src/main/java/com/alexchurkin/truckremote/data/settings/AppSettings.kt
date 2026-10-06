package com.alexchurkin.truckremote.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.alexchurkin.truckremote.domain.SteeringCurve
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
        // Analog unless the user chose the digital pedals
        fun fromPrefValue(value: String?) = entries.firstOrNull { it.prefValue == value } ?: Analog
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
        get() = prefs.getBoolean(KEY_FORCE_FEEDBACK, true)
        set(value) = prefs.edit { putBoolean(KEY_FORCE_FEEDBACK, value) }

    // Strength of the vibration, percent (see VIBRATION_STRENGTH_RANGE)
    var vibrationStrength: Int
        get() = prefs.getInt(KEY_VIBRATION_STRENGTH, DEFAULT_VIBRATION_STRENGTH).coerceIn(VIBRATION_STRENGTH_RANGE)
        set(value) = prefs.edit { putInt(KEY_VIBRATION_STRENGTH, value.coerceIn(VIBRATION_STRENGTH_RANGE)) }

    // The continuous vibration of the road surface
    var roadVibration: Boolean
        get() = prefs.getBoolean(KEY_ROAD_VIBRATION, true)
        set(value) = prefs.edit { putBoolean(KEY_ROAD_VIBRATION, value) }

    // Small clicks of the blinkers, the gearbox and the retarder
    var dashboardClicks: Boolean
        get() = prefs.getBoolean(KEY_DASHBOARD_CLICKS, true)
        set(value) = prefs.edit { putBoolean(KEY_DASHBOARD_CLICKS, value) }

    // The app starts with the dashboard (a tablet or a second phone beside the controller)
    var dashboardOnStart: Boolean
        get() = prefs.getBoolean(KEY_DASHBOARD_ON_START, false)
        set(value) = prefs.edit { putBoolean(KEY_DASHBOARD_ON_START, value) }

    var pneumaticHorn: Boolean
        get() = prefs.getBoolean(KEY_PNEUMATIC_HORN, false)
        set(value) = prefs.edit { putBoolean(KEY_PNEUMATIC_HORN, value) }

    // Speed, cruise control and other instruments in the middle of the controller screen
    var showDashboard: Boolean
        get() = prefs.getBoolean(KEY_SHOW_DASHBOARD, true)
        set(value) = prefs.edit { putBoolean(KEY_SHOW_DASHBOARD, value) }

    /**
     * Steering, the quick actions layout and speed units: common for both games
     * unless [separateGameSettings] is on (American Truck Simulator has its own ones then).
     */
    fun game(game: Game) = GameSettings(prefs, if (separateGameSettings && game == Game.Ats) ATS_PREFIX else "")

    // American Truck Simulator starts with a copy of the common settings
    var separateGameSettings: Boolean
        get() = prefs.getBoolean(KEY_SEPARATE_GAME_SETTINGS, false)
        set(value) {
            if (value && !separateGameSettings) GameSettings(prefs, ATS_PREFIX).copyFrom(GameSettings(prefs, ""))
            prefs.edit { putBoolean(KEY_SEPARATE_GAME_SETTINGS, value) }
        }

    // The game the server reported last (its settings are used until the game is known again)
    var lastGame: Game
        get() = Game.fromPrefValue(prefs.getString(KEY_LAST_GAME, null))
        set(value) = prefs.edit { putString(KEY_LAST_GAME, value.prefValue) }

    // The server revision the "update the server" hint was shown for: it is shown once
    var serverUpdateHintRevision: Int
        get() = prefs.getInt(KEY_SERVER_UPDATE_HINT, 0)
        set(value) = prefs.edit { putInt(KEY_SERVER_UPDATE_HINT, value) }

    // The controls are paused while the phone lies (screen down or up)
    var autoPause: Boolean
        get() = prefs.getBoolean(KEY_AUTO_PAUSE, true)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_PAUSE, value) }

    var pedalMode: PedalMode
        get() = PedalMode.fromPrefValue(prefs.getString(KEY_PEDAL_MODE, null))
        set(value) = prefs.edit { putString(KEY_PEDAL_MODE, value.prefValue) }

    var throttleLock: Boolean
        get() = prefs.getBoolean(KEY_THROTTLE_LOCK, true)
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
        val VIBRATION_STRENGTH_RANGE = 10..100
        const val DEFAULT_VIBRATION_STRENGTH = 70
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
        private const val KEY_DASHBOARD_ON_START = "dashboardOnStart"
        private const val KEY_VIBRATION_STRENGTH = "vibrationStrength"
        private const val KEY_ROAD_VIBRATION = "roadVibration"
        private const val KEY_DASHBOARD_CLICKS = "dashboardClicks"
        private const val KEY_LAST_SERVER_IP = "lastServerIp"
        private const val KEY_PNEUMATIC_HORN = "pneumaticSignal"
        private const val KEY_SHOW_DASHBOARD = "showDashboard"
        private const val KEY_SEPARATE_GAME_SETTINGS = "separateGameSettings"
        private const val KEY_LAST_GAME = "lastGame"
        private const val ATS_PREFIX = "ats."
        private const val KEY_SERVER_UPDATE_HINT = "serverUpdateHintRevision"
        private const val KEY_AUTO_PAUSE = "autoPause"
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

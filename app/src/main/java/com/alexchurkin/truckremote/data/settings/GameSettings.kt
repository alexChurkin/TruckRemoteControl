package com.alexchurkin.truckremote.data.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import com.alexchurkin.truckremote.domain.SteeringProcessor

enum class Game(val prefValue: String) {
    Ets2("ets2"),
    Ats("ats"),
    ;

    companion object {
        fun fromPrefValue(value: String?) = entries.firstOrNull { it.prefValue == value } ?: Ets2
    }
}

enum class SpeedUnits(val prefValue: String) {
    // km/h in Euro Truck Simulator 2, mph in American Truck Simulator
    ByGame("game"),
    Metric("metric"),
    Imperial("imperial"),
    ;

    fun isImperial(game: Game) = this == Imperial || (this == ByGame && game == Game.Ats)

    companion object {
        fun fromPrefValue(value: String?) = entries.firstOrNull { it.prefValue == value } ?: ByGame
    }
}

/**
 * Settings that may differ between the games: steering, the quick actions layout, speed units.
 * Keys of a profile start with [prefix] (empty for the common profile, as in previous versions).
 */
class GameSettings internal constructor(private val prefs: SharedPreferences, private val prefix: String) {

    // Previous versions had only a switch, its dead zone was about 6 degrees
    var steeringDeadZone: Int
        get() = prefs.getInt(
            prefix + KEY_STEERING_DEAD_ZONE,
            if (prefix.isEmpty() && prefs.getBoolean(KEY_DEAD_ZONE, false)) LEGACY_DEAD_ZONE else 0,
        )
            .coerceIn(AppSettings.STEERING_DEAD_ZONE_RANGE)
        set(value) = prefs.edit { putInt(prefix + KEY_STEERING_DEAD_ZONE, value) }

    var steeringMaxAngle: Int
        get() = prefs.getInt(prefix + KEY_STEERING_MAX_ANGLE, AppSettings.DEFAULT_STEERING_MAX_ANGLE)
            .coerceIn(AppSettings.STEERING_MAX_ANGLE_RANGE)
        set(value) = prefs.edit { putInt(prefix + KEY_STEERING_MAX_ANGLE, value) }

    var steeringExponent: Float
        get() = prefs.getFloat(prefix + KEY_STEERING_EXPONENT, AppSettings.DEFAULT_STEERING_EXPONENT)
            .coerceIn(AppSettings.STEERING_EXPONENT_RANGE)
        set(value) = prefs.edit { putFloat(prefix + KEY_STEERING_EXPONENT, value) }

    // See SteeringProcessor.smoothness
    var steeringSmoothness: Int
        get() = prefs.getInt(prefix + KEY_STEERING_SMOOTHNESS, SteeringProcessor.DEFAULT_SMOOTHNESS)
            .coerceIn(SteeringProcessor.SMOOTHNESS_RANGE)
        set(value) = prefs.edit { putInt(prefix + KEY_STEERING_SMOOTHNESS, value) }

    var actionLayout: ActionLayout
        get() = ActionLayout.decode(prefs.getString(prefix + KEY_ACTION_LAYOUT, null))
        set(value) = prefs.edit { putString(prefix + KEY_ACTION_LAYOUT, value.encode()) }

    var speedUnits: SpeedUnits
        get() = SpeedUnits.fromPrefValue(prefs.getString(prefix + KEY_SPEED_UNITS, null))
        set(value) = prefs.edit { putString(prefix + KEY_SPEED_UNITS, value.prefValue) }

    fun copyFrom(other: GameSettings) {
        steeringDeadZone = other.steeringDeadZone
        steeringMaxAngle = other.steeringMaxAngle
        steeringExponent = other.steeringExponent
        steeringSmoothness = other.steeringSmoothness
        actionLayout = other.actionLayout
        speedUnits = other.speedUnits
    }

    private companion object {
        const val KEY_DEAD_ZONE = "deadzone"
        const val LEGACY_DEAD_ZONE = 6
        const val KEY_STEERING_DEAD_ZONE = "steeringDeadZone"
        const val KEY_STEERING_MAX_ANGLE = "steeringMaxAngle"
        const val KEY_STEERING_EXPONENT = "steeringExponent"
        const val KEY_STEERING_SMOOTHNESS = "steeringSmoothness"
        const val KEY_ACTION_LAYOUT = "actionLayout"
        const val KEY_SPEED_UNITS = "speedUnits"
    }
}

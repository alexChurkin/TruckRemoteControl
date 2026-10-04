package com.alexchurkin.truckremote.data.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Surface
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Gravity projection on the device Y axis (m/s²) and whether the screen is in reverse landscape
 * (the steering direction is opposite then).
 */
data class TiltReading(val y: Float, val reverseLandscape: Boolean)

interface TiltSensor {
    // "gravity" or "accelerometer", for analytics
    val kind: String

    // Readings while collected, the sensor is turned off when the collection stops
    fun readings(): Flow<TiltReading>
}

/*
 * Gravity sensor isn't affected by shaking and touches of the screen (e.g. pressing pedals).
 * Without a gyroscope it is only a filtered accelerometer with a delay, so the accelerometer is used then.
 */
class AndroidTiltSensor(context: Context) : TiltSensor {

    private val sensorManager = ContextCompat.getSystemService(context, SensorManager::class.java)
    private val display: Display? = ContextCompat.getSystemService(context, DisplayManager::class.java)
        ?.getDisplay(Display.DEFAULT_DISPLAY)

    private val sensor: Sensor? = sensorManager?.let { manager ->
        val hasGyroscope = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
        val gravity = if (hasGyroscope) manager.getDefaultSensor(Sensor.TYPE_GRAVITY) else null
        gravity ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    override val kind: String
        get() = if (sensor?.type == Sensor.TYPE_GRAVITY) "gravity" else "accelerometer"

    override fun readings(): Flow<TiltReading> {
        val manager = sensorManager ?: return emptyFlow()
        val tiltSensor = sensor ?: return emptyFlow()
        return callbackFlow {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    trySend(TiltReading(event.values[1], display?.rotation == Surface.ROTATION_270))
                }

                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
            }
            // The state is sent ~60 times per second, the game rate is enough
            manager.registerListener(listener, tiltSensor, SensorManager.SENSOR_DELAY_GAME)
            awaitClose { manager.unregisterListener(listener) }
        }
    }
}

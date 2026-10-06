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
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * The steering angle of the phone held sideways: degrees of its rotation in the plane of the screen,
 * positive to the right (reverse landscape is taken into account), and the time of the measurement.
 * [screenUp] is where the screen faces: 1 - straight up, 0 - sideways, -1 - straight down.
 */
data class TiltReading(val angle: Float, val timeNanos: Long, val screenUp: Float = 0f)

interface TiltSensor {
    // "rotation_vector", "gravity" or "accelerometer", for analytics
    val kind: String

    // Readings while collected, the sensor is turned off when the collection stops
    fun readings(): Flow<TiltReading>
}

/*
 * The best available source of the gravity direction:
 * - game rotation vector: the gyroscope and the accelerometer fused by the system (smooth, quick,
 *   not affected by touches of the screen; the magnetometer isn't used, so magnets nearby don't matter);
 * - gravity sensor: a fusion too on devices with a gyroscope;
 * - accelerometer: noisy, the steering filter of the app smooths it.
 * The angle is taken in the plane of the screen (atan2), so the sensitivity doesn't depend on how far
 * the phone is tilted back.
 */
class AndroidTiltSensor(context: Context) : TiltSensor {

    private val sensorManager = ContextCompat.getSystemService(context, SensorManager::class.java)
    private val display: Display? = ContextCompat.getSystemService(context, DisplayManager::class.java)
        ?.getDisplay(Display.DEFAULT_DISPLAY)

    private val sensor: Sensor? = sensorManager?.let { manager ->
        val hasGyroscope = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
        val fused = if (hasGyroscope) {
            manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
                ?: manager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        } else {
            null
        }
        fused ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    override val kind: String
        get() = when (sensor?.type) {
            Sensor.TYPE_GAME_ROTATION_VECTOR -> "rotation_vector"
            Sensor.TYPE_GRAVITY -> "gravity"
            else -> "accelerometer"
        }

    override fun readings(): Flow<TiltReading> {
        val manager = sensorManager ?: return emptyFlow()
        val tiltSensor = sensor ?: return emptyFlow()
        val rotation = FloatArray(ROTATION_MATRIX_SIZE)
        return callbackFlow {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val gravityX: Float
                    val gravityY: Float
                    val screenUp: Float
                    if (event.sensor.type == Sensor.TYPE_GAME_ROTATION_VECTOR) {
                        // The world "up" axis in the device coordinates: the last row of the rotation matrix
                        SensorManager.getRotationMatrixFromVector(rotation, event.values)
                        gravityX = rotation[UP_X]
                        gravityY = rotation[UP_Y]
                        screenUp = rotation[UP_Z]
                    } else {
                        gravityX = event.values[0]
                        gravityY = event.values[1]
                        val norm = sqrt(gravityX * gravityX + gravityY * gravityY + event.values[2] * event.values[2])
                        screenUp = if (norm > 0f) event.values[2] / norm else 0f
                    }
                    val angle = Math.toDegrees(atan2(gravityY, abs(gravityX)).toDouble()).toFloat()
                    val reverse = display?.rotation == Surface.ROTATION_270
                    trySend(TiltReading(if (reverse) -angle else angle, event.timestamp, screenUp))
                }

                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
            }
            // 100 Hz: the state is sent ~60 times per second, the filter gets fresh values
            manager.registerListener(listener, tiltSensor, SAMPLING_PERIOD_US)
            awaitClose { manager.unregisterListener(listener) }
        }
    }

    private companion object {
        const val SAMPLING_PERIOD_US = 10_000
        const val ROTATION_MATRIX_SIZE = 9
        const val UP_X = 6
        const val UP_Y = 7
        const val UP_Z = 8
    }
}

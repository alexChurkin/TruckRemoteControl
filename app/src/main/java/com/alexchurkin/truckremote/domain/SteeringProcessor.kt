package com.alexchurkin.truckremote.domain

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * The steering value from the angle of the phone: the noise is filtered (adaptively, see [OneEuroFilter]),
 * the calibration offset is added and the curve of the settings is applied.
 */
class SteeringProcessor {

    private var filter = OneEuroFilter()

    /**
     * 1 (responsive: the quickest reaction, a little jitter may pass) .. 10 (smooth: no jitter, a slower reaction);
     * [DEFAULT_SMOOTHNESS] is the tuned middle. Every 2 levels halve (or double) the smoothing at rest.
     */
    var smoothness = DEFAULT_SMOOTHNESS
        set(value) {
            val level = value.coerceIn(SMOOTHNESS_RANGE)
            if (field == level) return
            field = level
            val factor = 2f.pow((DEFAULT_SMOOTHNESS - level) / 2f)
            filter = OneEuroFilter(
                minCutoff = OneEuroFilter.DEFAULT_MIN_CUTOFF * factor,
                beta = OneEuroFilter.DEFAULT_BETA * sqrt(factor),
            )
        }

    var curve = SteeringCurve(deadZoneDeg = 0, maxAngleDeg = DEFAULT_MAX_ANGLE, exponent = 1f)

    // Degrees added to the angle: the straight position chosen by the user
    var calibrationOffset = 0f

    // The filtered angle without the calibration offset, the straight position for the calibration
    var lastAngle = 0f
        private set

    fun process(angleDeg: Float, timeNanos: Long): Float {
        lastAngle = filter.filter(angleDeg, timeNanos)
        return curve.apply(lastAngle + calibrationOffset)
    }

    companion object {
        const val DEFAULT_SMOOTHNESS = 5
        val SMOOTHNESS_RANGE = 1..10
        private const val DEFAULT_MAX_ANGLE = 90
    }
}

package com.alexchurkin.truckremote.domain

/**
 * The steering value from the angle of the phone: the noise is filtered (adaptively, see [OneEuroFilter]),
 * the calibration offset is added and the curve of the settings is applied.
 */
class SteeringProcessor {

    private val filter = OneEuroFilter()

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

    private companion object {
        const val DEFAULT_MAX_ANGLE = 90
    }
}

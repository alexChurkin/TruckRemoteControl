package com.alexchurkin.truckremote.domain

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.withSign

/**
 * Converts the steering angle of the phone (degrees, positive to the right) into the steering value
 * sent to the server: [GRAVITY] (m/s², the unit of previous versions) is the full lock.
 * - an angle within [deadZoneDeg] gives 0, the rest of the range starts from 0 (no jump at the dead zone border);
 * - [maxAngleDeg] and more give the full lock;
 * - [exponent] > 1 makes the center softer and the edges sharper.
 * Every degree of the range turns the wheel equally (the angle, not the gravity projection, is used).
 */
class SteeringCurve(deadZoneDeg: Int, maxAngleDeg: Int, private val exponent: Float) {

    private val deadZone = deadZoneDeg.toFloat()
    private val range = (maxAngleDeg - deadZoneDeg).toFloat().coerceAtLeast(MIN_RANGE_DEG)

    fun apply(angleDeg: Float): Float {
        if (angleDeg.isNaN()) return 0f
        val angle = abs(angleDeg)
        if (angle <= deadZone) return 0f
        val part = ((angle - deadZone) / range).coerceAtMost(1f)
        return (part.pow(exponent) * GRAVITY).withSign(angleDeg)
    }

    companion object {
        const val GRAVITY = 9.80665f
        private const val MIN_RANGE_DEG = 1f
    }
}

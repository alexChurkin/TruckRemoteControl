package com.alexchurkin.truckremote.domain

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.withSign

/**
 * Converts the gravity projection on the device Y axis (m/s², positive to the right)
 * into the steering value sent to the server (in the same units, so the server scale is kept).
 *
 * Work is done with the tilt sine (y / g):
 * - tilt below [deadZoneDeg] gives 0, the rest of the range starts from 0 (no jump at the dead zone border);
 * - tilt of [maxAngleDeg] and more gives the full value (g);
 * - [exponent] > 1 makes the center softer and the edges sharper.
 * Dead zone 0°, max angle 90° and exponent 1 give the input value as is (behavior of previous versions).
 */
class SteeringCurve(deadZoneDeg: Int, maxAngleDeg: Int, private val exponent: Float) {

    private val deadZoneSin = sinDeg(deadZoneDeg)
    private val range = (sinDeg(maxAngleDeg) - deadZoneSin).coerceAtLeast(MIN_RANGE)

    fun apply(y: Float): Float {
        if (y.isNaN()) return 0f
        val tiltSin = (abs(y) / GRAVITY).coerceAtMost(1f)
        if (tiltSin <= deadZoneSin) return 0f
        val part = ((tiltSin - deadZoneSin) / range).coerceAtMost(1f)
        return (part.pow(exponent) * GRAVITY).withSign(y)
    }

    companion object {
        const val GRAVITY = 9.80665f
        private const val MIN_RANGE = 0.01f

        private fun sinDeg(degrees: Int) = sin(Math.toRadians(degrees.toDouble())).toFloat()
    }
}

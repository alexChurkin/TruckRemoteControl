package com.alexchurkin.truckremote.domain

import kotlin.math.PI
import kotlin.math.abs

/**
 * Adaptive low-pass filter for noisy input (Casiez, Roussel, Vogel, "1€ Filter", CHI 2012):
 * a slowly changing value is smoothed strongly (no jitter while the wheel is held still),
 * a fast change passes almost as is (no lag in a quick turn).
 * [minCutoff] (Hz) sets the smoothing at rest, [beta] how fast the cutoff grows with the speed of the change.
 */
class OneEuroFilter(
    private val minCutoff: Float = DEFAULT_MIN_CUTOFF,
    private val beta: Float = DEFAULT_BETA,
    private val derivativeCutoff: Float = DEFAULT_DERIVATIVE_CUTOFF,
) {
    private var lastValue = 0f
    private var lastDerivative = 0f
    private var lastTimeNanos = 0L
    private var initialized = false

    fun filter(value: Float, timeNanos: Long): Float {
        val gap = (timeNanos - lastTimeNanos) / NANOS_IN_SECOND
        // After a pause (e.g. the sensor was off) the old value isn't followed slowly, the filter starts anew
        if (!initialized || value.isNaN() || gap > RESTART_GAP) {
            initialized = !value.isNaN()
            lastValue = if (value.isNaN()) 0f else value
            lastDerivative = 0f
            lastTimeNanos = timeNanos
            return lastValue
        }
        val dt = gap.coerceIn(MIN_DT, MAX_DT)
        lastTimeNanos = timeNanos

        val derivative = (value - lastValue) / dt
        lastDerivative += alpha(derivativeCutoff, dt) * (derivative - lastDerivative)
        val cutoff = minCutoff + beta * abs(lastDerivative)
        lastValue += alpha(cutoff, dt) * (value - lastValue)
        return lastValue
    }

    private fun alpha(cutoff: Float, dt: Float): Float {
        val tau = 1f / (2f * PI.toFloat() * cutoff)
        return 1f / (1f + tau / dt)
    }

    companion object {
        // Tuned for the steering angle in degrees
        const val DEFAULT_MIN_CUTOFF = 1.2f
        const val DEFAULT_BETA = 0.04f
        private const val DEFAULT_DERIVATIVE_CUTOFF = 1f
        private const val NANOS_IN_SECOND = 1_000_000_000f

        private const val MIN_DT = 0.001f
        private const val MAX_DT = 0.1f
        private const val RESTART_GAP = 0.25f
    }
}

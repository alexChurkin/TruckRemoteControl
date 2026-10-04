package com.alexchurkin.truckremote.domain

import com.alexchurkin.truckremote.domain.SteeringCurve.Companion.GRAVITY
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SteeringCurveTest {

    private fun tilt(degrees: Double) = (GRAVITY * sin(Math.toRadians(degrees))).toFloat()

    @Test
    fun `default curve keeps the input value`() {
        val curve = SteeringCurve(deadZoneDeg = 0, maxAngleDeg = 90, exponent = 1f)
        listOf(-9f, -2.5f, 0f, 0.3f, 4f, 9.7f).forEach { assertEquals(it, curve.apply(it), 0.001f) }
    }

    @Test
    fun `dead zone gives zero and the rest of range starts from zero`() {
        val curve = SteeringCurve(deadZoneDeg = 6, maxAngleDeg = 90, exponent = 1f)
        assertEquals(0f, curve.apply(tilt(5.0)), 0f)
        assertEquals(0f, curve.apply(-tilt(5.0)), 0f)
        val justOutside = curve.apply(tilt(6.5))
        assertTrue(justOutside > 0f && justOutside < 0.2f)
        assertEquals(GRAVITY, curve.apply(GRAVITY), 0.001f)
    }

    @Test
    fun `max angle gives the full value`() {
        val curve = SteeringCurve(deadZoneDeg = 0, maxAngleDeg = 45, exponent = 1f)
        assertEquals(GRAVITY, curve.apply(tilt(45.0)), 0.001f)
        assertEquals(-GRAVITY, curve.apply(-tilt(70.0)), 0.001f)
        assertEquals(GRAVITY / 2, curve.apply(tilt(45.0) / 2), 0.001f)
    }

    @Test
    fun `exponent softens the center and keeps the edges`() {
        val curve = SteeringCurve(deadZoneDeg = 0, maxAngleDeg = 90, exponent = 2f)
        assertEquals(GRAVITY / 4, curve.apply(GRAVITY / 2), 0.001f)
        assertEquals(-GRAVITY / 4, curve.apply(-GRAVITY / 2), 0.001f)
        assertEquals(GRAVITY, curve.apply(GRAVITY), 0.001f)
    }

    @Test
    fun `dead zone bigger than max angle doesn't break the curve`() {
        val curve = SteeringCurve(deadZoneDeg = 20, maxAngleDeg = 20, exponent = 1f)
        assertEquals(0f, curve.apply(tilt(10.0)), 0f)
        assertEquals(GRAVITY, curve.apply(tilt(25.0)), 0.001f)
        assertEquals(0f, curve.apply(Float.NaN), 0f)
    }
}

package com.alexchurkin.truckremote.domain

import com.alexchurkin.truckremote.domain.SteeringCurve.Companion.GRAVITY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SteeringCurveTest {

    @Test
    fun `angle is proportional to the steering`() {
        val curve = SteeringCurve(deadZoneDeg = 0, maxAngleDeg = 90, exponent = 1f)
        assertEquals(GRAVITY / 2, curve.apply(45f), 0.001f)
        assertEquals(-GRAVITY / 3, curve.apply(-30f), 0.001f)
        assertEquals(0f, curve.apply(0f), 0f)
    }

    @Test
    fun `dead zone gives zero and the rest of range starts from zero`() {
        val curve = SteeringCurve(deadZoneDeg = 6, maxAngleDeg = 90, exponent = 1f)
        assertEquals(0f, curve.apply(5f), 0f)
        assertEquals(0f, curve.apply(-5f), 0f)
        val justOutside = curve.apply(6.5f)
        assertTrue(justOutside > 0f && justOutside < 0.2f)
        assertEquals(GRAVITY, curve.apply(90f), 0.001f)
    }

    @Test
    fun `max angle gives the full lock`() {
        val curve = SteeringCurve(deadZoneDeg = 0, maxAngleDeg = 45, exponent = 1f)
        assertEquals(GRAVITY, curve.apply(45f), 0.001f)
        assertEquals(-GRAVITY, curve.apply(-70f), 0.001f)
        assertEquals(GRAVITY / 2, curve.apply(22.5f), 0.001f)
    }

    @Test
    fun `exponent softens the center and keeps the edges`() {
        val curve = SteeringCurve(deadZoneDeg = 0, maxAngleDeg = 90, exponent = 2f)
        assertEquals(GRAVITY / 4, curve.apply(45f), 0.001f)
        assertEquals(-GRAVITY / 4, curve.apply(-45f), 0.001f)
        assertEquals(GRAVITY, curve.apply(90f), 0.001f)
    }

    @Test
    fun `dead zone bigger than max angle doesn't break the curve`() {
        val curve = SteeringCurve(deadZoneDeg = 20, maxAngleDeg = 20, exponent = 1f)
        assertEquals(0f, curve.apply(10f), 0f)
        assertEquals(GRAVITY, curve.apply(25f), 0.001f)
        assertEquals(0f, curve.apply(Float.NaN), 0f)
    }
}

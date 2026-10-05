package com.alexchurkin.truckremote.domain

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SteeringProcessorTest {

    private val stepNanos = 10_000_000L

    // The angle the processor shows 0.1 s after a quick turn from 0° to 30°
    private fun angleAfterTurn(smoothness: Int): Float {
        val processor = SteeringProcessor()
        processor.smoothness = smoothness
        processor.curve = SteeringCurve(deadZoneDeg = 0, maxAngleDeg = 90, exponent = 1f)
        repeat(50) { processor.process(0f, it * stepNanos) }
        var steering = 0f
        for (i in 1..10) steering = processor.process(30f, (50 + i) * stepNanos)
        return steering / SteeringCurve.GRAVITY * 90f
    }

    // The largest deviation from 10° when the wheel is held with ±0.5° of noise
    private fun jitter(smoothness: Int): Float {
        val processor = SteeringProcessor()
        processor.smoothness = smoothness
        processor.curve = SteeringCurve(deadZoneDeg = 0, maxAngleDeg = 90, exponent = 1f)
        var deviation = 0f
        repeat(300) { i ->
            val angle = processor.process(10f + if (i % 2 == 0) 0.5f else -0.5f, i * stepNanos) /
                SteeringCurve.GRAVITY * 90f
            if (i > 100) deviation = maxOf(deviation, abs(angle - 10f))
        }
        return deviation
    }

    @Test
    fun `responsive steering reacts quicker`() {
        val responsive = angleAfterTurn(1)
        val balanced = angleAfterTurn(SteeringProcessor.DEFAULT_SMOOTHNESS)
        val smooth = angleAfterTurn(10)
        assertTrue("$responsive > $balanced > $smooth", responsive > balanced && balanced > smooth)
    }

    @Test
    fun `smooth steering has less jitter`() {
        assertTrue(jitter(10) < jitter(SteeringProcessor.DEFAULT_SMOOTHNESS))
        assertTrue(jitter(SteeringProcessor.DEFAULT_SMOOTHNESS) < jitter(1))
    }

    @Test
    fun `smoothness is kept in its range`() {
        val processor = SteeringProcessor()
        processor.smoothness = 42
        assertEquals(SteeringProcessor.SMOOTHNESS_RANGE.last, processor.smoothness)
    }
}

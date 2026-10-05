package com.alexchurkin.truckremote.domain

import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OneEuroFilterTest {

    private val stepNanos = 10_000_000L

    @Test
    fun `first value passes as is`() {
        assertEquals(12f, OneEuroFilter().filter(12f, 0), 0f)
    }

    @Test
    fun `jitter of a held wheel is reduced`() {
        val filter = OneEuroFilter()
        var maxDeviation = 0f
        repeat(300) { i ->
            // ±0.5° of noise around 10°
            val noisy = 10f + if (i % 2 == 0) 0.5f else -0.5f
            val value = filter.filter(noisy, i * stepNanos)
            if (i > 100) maxDeviation = maxOf(maxDeviation, abs(value - 10f))
        }
        assertTrue("Deviation $maxDeviation", maxDeviation < 0.1f)
    }

    @Test
    fun `quick turn follows without a big lag`() {
        val filter = OneEuroFilter()
        repeat(50) { filter.filter(0f, it * stepNanos) }
        // 60° in 0.2 s, then held
        var value = 0f
        for (i in 1..20) value = filter.filter(i * 3f, (50 + i) * stepNanos)
        assertTrue("Lag ${60f - value}°", 60f - value < 8f)
        for (i in 21..40) value = filter.filter(60f, (50 + i) * stepNanos)
        assertEquals(60f, value, 1f)
    }

    @Test
    fun `slow movement is followed smoothly`() {
        val filter = OneEuroFilter()
        var value = 0f
        for (i in 0..300) {
            val angle = 20f * sin(i / 100f)
            value = filter.filter(angle, i * stepNanos)
        }
        assertEquals(20f * sin(3f), value, 1.5f)
    }

    @Test
    fun `pause starts the filter anew`() {
        val filter = OneEuroFilter()
        repeat(50) { filter.filter(0f, it * stepNanos) }
        // The sensor was off for a second: the new value isn't approached slowly
        assertEquals(30f, filter.filter(30f, 50 * stepNanos + 1_000_000_000L), 0f)
    }
}

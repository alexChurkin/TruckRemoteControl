package com.alexchurkin.truckremote.ui.main

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedingLevelTest {

    // m/s of a speed in km/h
    private fun kmh(value: Float) = value / 3.6f

    @Test
    fun `the speed turns amber and then red gradually over the limit`() {
        val limit = kmh(90f)
        assertEquals(0f, speedingLevel(kmh(85f), limit), DELTA)
        // At the limit and a bit over it the speed of a truck wavers: still white
        assertEquals(0f, speedingLevel(kmh(92f), limit), DELTA)
        assertEquals(0.5f, speedingLevel(kmh(94.5f), limit), DELTA)
        assertEquals(1f, speedingLevel(kmh(97f), limit), DELTA)
        assertEquals(1.5f, speedingLevel(kmh(100.5f), limit), DELTA)
        assertEquals(2f, speedingLevel(kmh(104f), limit), DELTA)
        assertEquals(2f, speedingLevel(kmh(150f), limit), DELTA)
    }

    @Test
    fun `reversing counts by its speed, no limit is no speeding`() {
        assertEquals(2f, speedingLevel(-kmh(40f), kmh(20f)), DELTA)
        assertEquals(0f, speedingLevel(kmh(150f), 0f), DELTA)
    }

    private companion object {
        const val DELTA = 0.01f
    }
}

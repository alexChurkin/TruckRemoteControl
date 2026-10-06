package com.alexchurkin.truckremote.data.device

import com.alexchurkin.truckremote.data.controller.HapticEvent
import com.alexchurkin.truckremote.data.controller.RoadFeel
import com.alexchurkin.truckremote.data.controller.RoadSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticPatternsTest {

    private fun List<HapticStep>.duration() = sumOf { it.ms }

    @Test
    fun `every event has a pattern that starts with a pulse and is short`() {
        HapticEvent.entries.forEach { event ->
            listOf(0.3f, 1f).forEach { strength ->
                val steps = HapticPatterns.of(event, strength)
                assertTrue("$event", steps.first() is HapticStep.Pulse)
                assertTrue("$event", steps.duration() in 1..MAX_EVENT_MS)
                assertTrue("$event", steps.all { it !is HapticStep.Pulse || it.level in 0f..1f })
            }
        }
    }

    @Test
    fun `dashboard clicks are much shorter than heavy events`() {
        val clicks = HapticEvent.entries.filter { it.isClick }.maxOf { HapticPatterns.of(it, 1f).duration() }
        val heavy = listOf(HapticEvent.Collision, HapticEvent.TrailerCoupled, HapticEvent.EngineStart)
            .minOf { HapticPatterns.of(it, 1f).duration() }

        assertTrue(clicks * 2 < heavy)
    }

    @Test
    fun `harder collision is stronger`() {
        val soft = HapticPatterns.of(HapticEvent.Collision, 0.4f).first() as HapticStep.Pulse
        val hard = HapticPatterns.of(HapticEvent.Collision, 1f).first() as HapticStep.Pulse

        assertTrue(hard.level > soft.level)
    }

    @Test
    fun `road vibration follows its level, a rumble strip is always felt`() {
        val weak = HapticPatterns.road(RoadFeel(0.2f, RoadSurface.Road)).filterIsInstance<HapticStep.Pulse>()
        val strong = HapticPatterns.road(RoadFeel(0.8f, RoadSurface.Road)).filterIsInstance<HapticStep.Pulse>()
        val strip = HapticPatterns.road(RoadFeel(0.1f, RoadSurface.RumbleStrip)).filterIsInstance<HapticStep.Pulse>()

        assertTrue(weak.maxOf { it.level } < strong.maxOf { it.level })
        assertEquals(0.6f, strip.first().level)
    }

    @Test
    fun `every primitive falls back to a click`() {
        HapticPrimitive.entries.forEach { primitive ->
            var current: HapticPrimitive? = primitive
            var steps = 0
            while (current != HapticPrimitive.Click && current != null && steps < HapticPrimitive.entries.size) {
                current = current.fallback
                steps++
            }
            assertEquals(HapticPrimitive.Click, current)
        }
    }

    private companion object {
        const val MAX_EVENT_MS = 700L
    }
}

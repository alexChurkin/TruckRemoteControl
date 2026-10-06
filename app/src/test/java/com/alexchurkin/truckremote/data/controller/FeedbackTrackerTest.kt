package com.alexchurkin.truckremote.data.controller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackTrackerTest {

    private val tracker = FeedbackTracker()

    private fun state(
        counters: Map<HapticEvent, Int>?,
        road: Float = 0f,
        telemetry: Boolean = true,
        ffbDurationMs: Long = 0,
    ) = ServerState(
        engineOn = true,
        parkingBrake = false,
        leftBlinker = false,
        rightBlinker = false,
        lightsMode = 0,
        ffbDurationMs = ffbDurationMs,
        dashboard = if (telemetry) DASHBOARD else null,
        haptics = counters?.let {
            HapticsState(road, RoadSurface.Offroad, it, it.mapValues { 0.5f })
        },
    )

    @Test
    fun `changed counters are events, the first state only gives them`() {
        val start = mapOf(HapticEvent.Collision to 3, HapticEvent.Blinker to 250)
        assertEquals(emptyList<Feedback>(), tracker.onState(state(start)))
        assertEquals(emptyList<Feedback>(), tracker.onState(state(start)))

        val events = tracker.onState(state(mapOf(HapticEvent.Collision to 4, HapticEvent.Blinker to 0)))

        assertEquals(
            listOf(Feedback.Event(HapticEvent.Collision, 0.5f), Feedback.Event(HapticEvent.Blinker, 0.5f)),
            events,
        )
    }

    @Test
    fun `road follows the haptics`() {
        tracker.onState(state(emptyMap(), road = 0.4f))
        assertEquals(RoadFeel(0.4f, RoadSurface.Offroad), tracker.road)

        tracker.onState(state(null))
        assertEquals(RoadFeel.None, tracker.road)
    }

    @Test
    fun `vjoy force feedback is a pulse only without the haptics`() {
        assertEquals(listOf(Feedback.Pulse(80)), tracker.onState(state(null, ffbDurationMs = 80)))
        assertEquals(
            listOf(Feedback.Pulse(80)),
            tracker.onState(state(emptyMap(), telemetry = false, ffbDurationMs = 80)),
        )
        assertEquals(emptyList<Feedback>(), tracker.onState(state(emptyMap(), ffbDurationMs = 80)))
    }

    @Test
    fun `counters of a new server session aren't events`() {
        val counters = HapticEvent.entries.associateWith { 5 }
        tracker.onState(state(counters))

        assertTrue(tracker.onState(state(HapticEvent.entries.associateWith { 0 })).isEmpty())
        assertEquals(1, tracker.onState(state(HapticEvent.entries.associateWith { if (it.id == 1) 1 else 0 })).size)
    }

    @Test
    fun `reset starts anew`() {
        tracker.onState(state(mapOf(HapticEvent.Fine to 1)))
        tracker.reset()

        assertEquals(emptyList<Feedback>(), tracker.onState(state(mapOf(HapticEvent.Fine to 2))))
    }

    private companion object {
        val DASHBOARD = Dashboard(
            speed = 0f,
            speedLimit = 0f,
            cruiseSpeed = 0f,
            gear = 0,
            engineRpm = 0,
            engineRpmMax = 0,
            fuelPercent = 0,
            isAts = false,
        )
    }
}

package com.alexchurkin.truckremote.data.controller

/**
 * Events the driver feels, found by the server in the telemetry (server revision 6+, see [BinaryProtocol]);
 * [id] is the id of the server. [priority]: a playing event isn't interrupted by a less important one.
 */
enum class HapticEvent(val id: Int, val priority: Int) {
    Collision(1, PRIORITY_HIGH),

    // A pothole, a kerb, a hard landing of a wheel
    Bump(2, PRIORITY_MEDIUM),
    GearShift(3, PRIORITY_LOW),

    // The relay of the blinkers: the lamp on (strength 1) and off (0.5)
    Blinker(4, PRIORITY_LOW),
    TrailerCoupled(5, PRIORITY_MEDIUM),
    TrailerUncoupled(6, PRIORITY_MEDIUM),
    EngineStart(7, PRIORITY_MEDIUM),
    EngineStop(8, PRIORITY_MEDIUM),
    ParkingBrake(9, PRIORITY_LOW),

    // A step of the retarder, the engine brake turned on or off
    Retarder(10, PRIORITY_LOW),

    // A warning lamp of the dashboard has come on
    Warning(11, PRIORITY_MEDIUM),
    Fine(12, PRIORITY_MEDIUM),

    // A tollgate, a ferry or a train was paid
    Payment(13, PRIORITY_LOW),
    JobDelivered(14, PRIORITY_MEDIUM),
    ;

    // The small clicks of the dashboard, they can be turned off in the settings
    val isClick: Boolean
        get() = this == GearShift || this == Blinker || this == Retarder

    companion object {
        fun byId(id: Int): HapticEvent? = entries.firstOrNull { it.id == id }
    }
}

private const val PRIORITY_LOW = 0
private const val PRIORITY_MEDIUM = 1
private const val PRIORITY_HIGH = 2

enum class RoadSurface {
    Road,
    Offroad,
    RumbleStrip,
}

// The haptics of a server state: the vibration of the road (0..1), the surface and the counters of the events
data class HapticsState(
    val road: Float,
    val surface: RoadSurface,
    val counters: Map<HapticEvent, Int>,
    val strengths: Map<HapticEvent, Float>,
)

// The continuous vibration of the road: [level] 0..1, 0 is still
data class RoadFeel(val level: Float, val surface: RoadSurface) {
    companion object {
        val None = RoadFeel(0f, RoadSurface.Road)
    }
}

// What the phone plays
sealed interface Feedback {
    // The force feedback of vJoy: older servers and the game without the telemetry plugin
    data class Pulse(val durationMs: Long) : Feedback

    // [strength] 0..1
    data class Event(val event: HapticEvent, val strength: Float) : Feedback
}

/**
 * Turns server states into what the phone plays. An event is played when its counter changes (the first state
 * after a connection only gives the counters). While the server has the telemetry, its haptics replace
 * the force feedback of vJoy (both come from the same collisions); without it the vJoy effects are pulses.
 */
class FeedbackTracker {

    private var counters: Map<HapticEvent, Int>? = null

    @Volatile
    var road: RoadFeel = RoadFeel.None
        private set

    @Synchronized
    fun reset() {
        counters = null
        road = RoadFeel.None
    }

    @Synchronized
    fun onState(state: ServerState): List<Feedback> {
        val haptics = state.haptics?.takeIf { state.dashboard != null }
        if (haptics == null) {
            reset()
            return if (state.ffbDurationMs > 0) listOf(Feedback.Pulse(state.ffbDurationMs)) else emptyList()
        }
        road = RoadFeel(haptics.road, haptics.surface)
        val before = counters
        counters = haptics.counters
        if (before == null) return emptyList()
        val events = haptics.counters.mapNotNull { (event, counter) ->
            if (before[event] == null || before[event] == counter) {
                null
            } else {
                Feedback.Event(event, haptics.strengths[event] ?: 1f)
            }
        }
        // So many events at once are a new session of the server (its counters start anew), not the game
        return if (events.size > MAX_EVENTS_AT_ONCE) emptyList() else events
    }

    private companion object {
        const val MAX_EVENTS_AT_ONCE = 3
    }
}

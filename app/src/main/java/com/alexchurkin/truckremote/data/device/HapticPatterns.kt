// The patterns are a table of timings and levels: naming each of the numbers wouldn't make them clearer
@file:Suppress("MagicNumber")

package com.alexchurkin.truckremote.data.device

import com.alexchurkin.truckremote.data.controller.HapticEvent
import com.alexchurkin.truckremote.data.controller.RoadFeel
import com.alexchurkin.truckremote.data.controller.RoadSurface

/**
 * A step of a vibration pattern: the motor is on for [ms] at [level] (0..1, it is scaled by the strength), or a pause.
 * Phones with haptic primitives play the [primitive] of a pulse instead (a short, crisp effect tuned by the maker
 * of the phone), the others vibrate for its time at its level (or just vibrate, without the amplitude control).
 */
sealed interface HapticStep {
    val ms: Long

    data class Pulse(override val ms: Long, val level: Float, val primitive: HapticPrimitive) : HapticStep

    data class Pause(override val ms: Long) : HapticStep
}

/**
 * Haptic primitives of VibrationEffect.Composition with their ids and the Android versions that have them.
 * A phone may lack some of them, then a similar one is played ([fallback]).
 */
enum class HapticPrimitive(val id: Int, val sinceSdk: Int) {
    Click(1, 30),
    Thud(2, 31),
    Spin(3, 31),
    QuickRise(4, 30),
    SlowRise(5, 30),
    QuickFall(6, 30),
    Tick(7, 30),
    LowTick(8, 31),
    ;

    val fallback: HapticPrimitive?
        get() = when (this) {
            Click -> null
            Thud, QuickRise, QuickFall, Tick -> Click
            LowTick -> Tick
            Spin, SlowRise -> QuickRise
        }
}

/**
 * What every event feels like. Heavy things (collisions, the trailer, the engine) are low and long with a tail,
 * the dashboard (blinkers, gears, retarder) are short clicks, money and warnings are rhythms that can't be taken
 * for the truck. [strength] 0..1 is the strength of the event (e.g. how hard the collision was).
 */
object HapticPatterns {

    // A table of the events
    @Suppress("LongMethod", "CyclomaticComplexMethod")
    fun of(event: HapticEvent, strength: Float): List<HapticStep> = when (event) {
        HapticEvent.Collision -> listOf(
            pulse(ms = 50, level = strength, primitive = HapticPrimitive.Thud),
            pulse(ms = 60, level = strength * 0.6f, primitive = HapticPrimitive.QuickFall),
            pause(ms = 30),
            pulse(ms = 80, level = strength * 0.35f, primitive = HapticPrimitive.LowTick),
            pulse(ms = 80, level = strength * 0.15f, primitive = HapticPrimitive.LowTick),
        )

        HapticEvent.Bump -> listOf(
            pulse(ms = 30, level = strength * 0.8f, primitive = HapticPrimitive.Thud),
            pulse(ms = 40, level = strength * 0.3f, primitive = HapticPrimitive.LowTick),
        )

        HapticEvent.GearShift -> listOf(
            pulse(ms = 14, level = strength * 0.6f, primitive = HapticPrimitive.Click),
            pause(ms = 25),
            pulse(ms = 12, level = strength * 0.35f, primitive = HapticPrimitive.LowTick),
        )

        // The relay clicks when the lamp goes on and softer when it goes off (strength 0.5)
        HapticEvent.Blinker -> listOf(
            pulse(
                ms = 8,
                level = strength * 0.55f,
                primitive = if (strength >= 1f) HapticPrimitive.Tick else HapticPrimitive.LowTick,
            ),
        )

        HapticEvent.Retarder -> listOf(
            pulse(ms = 10, level = 0.25f + strength * 0.35f, primitive = HapticPrimitive.Tick),
        )

        // The jaws of the fifth wheel lock: a click and a heavy clunk
        HapticEvent.TrailerCoupled -> listOf(
            pulse(ms = 25, level = strength, primitive = HapticPrimitive.Click),
            pause(ms = 70),
            pulse(ms = 40, level = strength * 0.75f, primitive = HapticPrimitive.Thud),
        )

        HapticEvent.TrailerUncoupled -> listOf(
            pulse(ms = 40, level = strength * 0.6f, primitive = HapticPrimitive.Thud),
            pulse(ms = 60, level = strength * 0.3f, primitive = HapticPrimitive.QuickFall),
        )

        // The starter cranks and the engine catches: a rising rumble that settles
        HapticEvent.EngineStart -> listOf(
            pulse(ms = 60, level = strength * 0.2f, primitive = HapticPrimitive.LowTick),
            pulse(ms = 60, level = strength * 0.4f, primitive = HapticPrimitive.LowTick),
            pulse(ms = 80, level = strength * 0.6f, primitive = HapticPrimitive.SlowRise),
            pulse(ms = 80, level = strength * 0.45f, primitive = HapticPrimitive.LowTick),
            pulse(ms = 80, level = strength * 0.35f, primitive = HapticPrimitive.LowTick),
            pulse(ms = 100, level = strength * 0.25f, primitive = HapticPrimitive.LowTick),
        )

        HapticEvent.EngineStop -> listOf(
            pulse(ms = 80, level = strength * 0.5f, primitive = HapticPrimitive.QuickFall),
            pulse(ms = 100, level = strength * 0.3f, primitive = HapticPrimitive.LowTick),
            pulse(ms = 120, level = strength * 0.15f, primitive = HapticPrimitive.LowTick),
        )

        // Applied (strength 1): the lever clicks and the air hisses out; released: a short rise
        HapticEvent.ParkingBrake -> if (strength >= 1f) {
            listOf(
                pulse(ms = 20, level = 0.6f, primitive = HapticPrimitive.Click),
                pause(ms = 30),
                pulse(ms = 120, level = 0.25f, primitive = HapticPrimitive.QuickFall),
                pulse(ms = 80, level = 0.12f, primitive = HapticPrimitive.LowTick),
            )
        } else {
            listOf(
                pulse(ms = 60, level = 0.2f, primitive = HapticPrimitive.QuickRise),
                pulse(ms = 40, level = 0.35f, primitive = HapticPrimitive.Tick),
            )
        }

        HapticEvent.Warning -> listOf(
            pulse(ms = 40, level = 0.5f + strength * 0.3f, primitive = HapticPrimitive.Click),
            pause(ms = 110),
            pulse(ms = 40, level = 0.5f + strength * 0.3f, primitive = HapticPrimitive.Click),
        )

        // Three heavy buzzes: something has gone wrong
        HapticEvent.Fine -> listOf(
            pulse(ms = 90, level = 0.9f, primitive = HapticPrimitive.Thud),
            pause(ms = 70),
            pulse(ms = 90, level = 0.9f, primitive = HapticPrimitive.Thud),
            pause(ms = 70),
            pulse(ms = 160, level = 0.9f, primitive = HapticPrimitive.Thud),
        )

        // A light "ka-ching"
        HapticEvent.Payment -> listOf(
            pulse(ms = 12, level = 0.5f, primitive = HapticPrimitive.Tick),
            pause(ms = 70),
            pulse(ms = 25, level = 0.8f, primitive = HapticPrimitive.Click),
        )

        // A rising "ta-da"
        HapticEvent.JobDelivered -> listOf(
            pulse(ms = 30, level = 0.4f, primitive = HapticPrimitive.QuickRise),
            pause(ms = 50),
            pulse(ms = 30, level = 0.6f, primitive = HapticPrimitive.Click),
            pause(ms = 50),
            pulse(ms = 80, level = 1f, primitive = HapticPrimitive.Spin),
        )
    }

    /**
     * One period of the vibration of the road, it is repeated while the road is the same. A rough road is a fine
     * uneven texture, offroad is slower and choppier, a rumble strip is an even fast drumming.
     */
    fun road(feel: RoadFeel): List<HapticStep> {
        val level = feel.level
        return when (feel.surface) {
            RoadSurface.Road -> listOf(1f, 0.55f, 0.85f, 0.4f, 0.95f, 0.6f, 0.75f, 0.45f)
                .map { pulse(ms = 20, level = level * it, primitive = HapticPrimitive.LowTick) }

            RoadSurface.Offroad -> listOf(1f, 0.3f, 0.8f, 0.2f, 0.9f, 0.4f)
                .map { pulse(ms = 40, level = level * it, primitive = HapticPrimitive.LowTick) }

            RoadSurface.RumbleStrip -> listOf(
                pulse(ms = 25, level = maxOf(level, 0.6f), primitive = HapticPrimitive.Tick),
                pause(ms = 25),
            )
        }
    }

    private fun pulse(ms: Long, level: Float, primitive: HapticPrimitive) =
        HapticStep.Pulse(ms, level.coerceIn(0f, 1f), primitive)

    private fun pause(ms: Long) = HapticStep.Pause(ms)
}

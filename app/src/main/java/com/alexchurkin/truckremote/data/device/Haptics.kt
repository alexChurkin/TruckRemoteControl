package com.alexchurkin.truckremote.data.device

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.alexchurkin.truckremote.data.controller.HapticEvent
import com.alexchurkin.truckremote.data.controller.RoadFeel
import kotlin.math.roundToInt

// What the vibration motor of the phone can do, the best first
enum class HapticCapability {
    // Haptic primitives of Android 11+: crisp effects tuned by the maker of the phone for its linear motor
    // (Pixel, Galaxy S and other phones with a good haptic motor)
    Primitives,

    // Vibration of a varying strength
    Amplitude,

    // On and off only (older or cheaper motors)
    OnOff,
    None,
}

/**
 * Vibration of the phone: the events of the game, the force feedback of vJoy and the continuous vibration
 * of the road under them. Called on the main thread. [intensity] 0..1 is the user's setting.
 */
interface Haptics {
    val capability: HapticCapability

    // [strength] 0..1 of the event itself (e.g. how hard the collision was)
    fun play(event: HapticEvent, strength: Float, intensity: Float)

    // A force feedback effect of vJoy (older servers, the game without the telemetry plugin)
    fun pulse(durationMs: Long, intensity: Float)

    // Lasts until it is changed; RoadFeel.None stops it. Events interrupt it and it comes back after them
    fun setRoad(feel: RoadFeel, intensity: Float)

    fun stop()
}

private fun defaultVibrator(context: Context): Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    ContextCompat.getSystemService(context, VibratorManager::class.java)?.defaultVibrator
} else {
    @Suppress("DEPRECATION")
    ContextCompat.getSystemService(context, Vibrator::class.java)
}

class AndroidHaptics(context: Context) : Haptics {

    private val vibrator: Vibrator? = defaultVibrator(context)?.takeIf { it.hasVibrator() }

    private val hasAmplitude = vibrator != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        vibrator.hasAmplitudeControl()

    // Primitives the phone plays, with their durations (ms)
    private val primitives: Map<HapticPrimitive, Long> = supportedPrimitives()

    override val capability = when {
        vibrator == null -> HapticCapability.None
        HapticPrimitive.Click in primitives && HapticPrimitive.Tick in primitives -> HapticCapability.Primitives
        hasAmplitude -> HapticCapability.Amplitude
        else -> HapticCapability.OnOff
    }

    private val handler = Handler(Looper.getMainLooper())

    // The event being played: the road waits for its end, less important events are skipped meanwhile
    private var eventPriority = 0
    private var eventEnd = 0L
    private var road = RoadFeel.None

    // The road vibration being played (null: none, it should be started)
    private var playingRoad: RoadFeel? = null
    private val resumeRoad = Runnable { applyRoad() }

    override fun play(event: HapticEvent, strength: Float, intensity: Float) {
        if (vibrator == null || intensity <= 0f) return
        val now = SystemClock.uptimeMillis()
        if (now < eventEnd && event.priority < eventPriority) return
        val steps = HapticPatterns.of(event, strength).scaled(intensity)
        val duration = if (capability == HapticCapability.Primitives) {
            playPrimitives(steps)
        } else {
            playWaveform(steps, repeat = false)
        }
        startEvent(event.priority, now + duration)
    }

    override fun pulse(durationMs: Long, intensity: Float) {
        if (vibrator == null || intensity <= 0f || durationMs <= 0) return
        val now = SystemClock.uptimeMillis()
        if (now < eventEnd) return
        val duration = durationMs.coerceAtMost(MAX_PULSE_MS)
        playWaveform(
            listOf(HapticStep.Pulse(duration, intensity, HapticPrimitive.Thud)),
            repeat = false,
        )
        startEvent(PULSE_PRIORITY, now + duration)
    }

    override fun setRoad(feel: RoadFeel, intensity: Float) {
        // Fine changes of the level aren't felt, and restarting the vibration for each of them would break it
        val level = (feel.level * intensity * ROAD_STEPS).roundToInt() / ROAD_STEPS.toFloat()
        road = if (level < ROAD_MIN_LEVEL) RoadFeel.None else RoadFeel(level, feel.surface)
        if (SystemClock.uptimeMillis() >= eventEnd) applyRoad()
    }

    override fun stop() {
        handler.removeCallbacks(resumeRoad)
        road = RoadFeel.None
        playingRoad = null
        eventEnd = 0
        vibrator?.cancel()
    }

    private fun startEvent(priority: Int, end: Long) {
        eventPriority = priority
        eventEnd = end
        playingRoad = null
        handler.removeCallbacks(resumeRoad)
        handler.postAtTime(resumeRoad, end)
    }

    private fun applyRoad() {
        val vibrator = vibrator ?: return
        if (road == playingRoad) return
        playingRoad = road
        if (road == RoadFeel.None) {
            vibrator.cancel()
            return
        }
        playWaveform(HapticPatterns.road(road), repeat = true)
    }

    // Returns the duration. HapticPrimitive.id are the values of the Composition.PRIMITIVE_* constants (lint can't
    // tell it); the constants themselves can't be used: some of them are only in newer Android versions
    @SuppressLint("WrongConstant")
    private fun playPrimitives(steps: List<HapticStep>): Long {
        val vibrator = vibrator ?: return 0
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return playWaveform(steps, repeat = false)
        val composition = VibrationEffect.startComposition()
        var delay = 0
        var duration = 0L
        steps.forEach { step ->
            when (step) {
                is HapticStep.Pause -> delay += step.ms.toInt()

                is HapticStep.Pulse -> {
                    val primitive = playable(step.primitive) ?: return@forEach
                    composition.addPrimitive(primitive.id, step.level.coerceIn(MIN_SCALE, 1f), delay)
                    duration += delay + (primitives[primitive] ?: step.ms)
                    delay = 0
                }
            }
        }
        vibrate(vibrator, composition.compose())
        return duration
    }

    /*
     * Steps as a waveform: the levels as amplitudes, or as on and off for motors without the amplitude control
     * (a weaker pulse is shorter then). Returns the duration of one period.
     */
    private fun playWaveform(steps: List<HapticStep>, repeat: Boolean): Long {
        val vibrator = vibrator ?: return 0
        val (timings, amplitudes) = waveform(steps, repeat)
        val repeatFrom = if (repeat) 0 else -1
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            @Suppress("DEPRECATION")
            vibrator.vibrate(onOffTimings(timings, amplitudes), repeatFrom)
            return timings.sum()
        }
        val effect = if (hasAmplitude) {
            VibrationEffect.createWaveform(timings.toLongArray(), amplitudes.toIntArray(), repeatFrom)
        } else {
            VibrationEffect.createWaveform(onOffTimings(timings, amplitudes), repeatFrom)
        }
        vibrate(vibrator, effect)
        return timings.sum()
    }

    // Timings and amplitudes of the segments, starting with an empty one
    private fun waveform(steps: List<HapticStep>, repeat: Boolean): Pair<List<Long>, List<Int>> {
        val timings = mutableListOf(0L)
        val amplitudes = mutableListOf(0)
        steps.forEach { step ->
            val level = (step as? HapticStep.Pulse)?.level ?: 0f
            if (level <= 0f || hasAmplitude) {
                timings += step.ms
                amplitudes += amplitude(level)
                return@forEach
            }
            // Without the amplitude control a weaker pulse is shorter, the rest of a road step is off
            val share = if (repeat) level else ON_OFF_BASE + (1 - ON_OFF_BASE) * level
            val on = (step.ms * share).toLong().coerceAtLeast(MIN_ON_MS)
            timings += on
            amplitudes += MAX_AMPLITUDE
            if (repeat) {
                timings += (step.ms - on).coerceAtLeast(0)
                amplitudes += 0
            }
        }
        return timings to amplitudes
    }

    private fun amplitude(level: Float) =
        if (level > 0f) (level * MAX_AMPLITUDE).roundToInt().coerceIn(MIN_AMPLITUDE, MAX_AMPLITUDE) else 0

    // Alternating off and on timings (starting with off), neighbouring segments of the same state joined
    private fun onOffTimings(timings: List<Long>, amplitudes: List<Int>): LongArray {
        val result = mutableListOf<Long>()
        var on = true
        timings.forEachIndexed { index, time ->
            val segmentOn = amplitudes[index] > 0
            if (segmentOn == on) result[result.lastIndex] += time else result += time
            on = segmentOn
        }
        return result.toLongArray()
    }

    // The primitive or its nearest replacement the phone has
    private fun playable(primitive: HapticPrimitive): HapticPrimitive? {
        var candidate: HapticPrimitive? = primitive
        while (candidate != null && candidate !in primitives) candidate = candidate.fallback
        return candidate
    }

    // Once at the start: the copies of the small arrays for the varargs don't matter (ids: see playPrimitives)
    @SuppressLint("WrongConstant")
    @Suppress("SpreadOperator")
    private fun supportedPrimitives(): Map<HapticPrimitive, Long> {
        val vibrator = vibrator
        if (vibrator == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyMap()
        val known = HapticPrimitive.entries.filter { Build.VERSION.SDK_INT >= it.sinceSdk }
        val supported = vibrator.arePrimitivesSupported(*known.map { it.id }.toIntArray())
        val available = known.filterIndexed { index, _ -> supported[index] }
        val durations = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            primitiveDurations(vibrator, available)
        } else {
            null
        }
        return available.mapIndexed { index, primitive ->
            primitive to (durations?.getOrNull(index)?.toLong() ?: ASSUMED_PRIMITIVE_MS)
        }.toMap()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    @SuppressLint("WrongConstant")
    @Suppress("SpreadOperator")
    private fun primitiveDurations(vibrator: Vibrator, primitives: List<HapticPrimitive>): IntArray =
        vibrator.getPrimitiveDurations(*primitives.map { it.id }.toIntArray())

    /*
     * Game vibration is media, not touch feedback: it follows the media vibration setting of Android 13+
     * and the game usage before that (so it isn't turned off together with the vibration of the keyboard).
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun vibrate(vibrator: Vibrator, effect: VibrationEffect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, mediaAttributes())
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, gameAudioAttributes)
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun mediaAttributes(): VibrationAttributes =
        VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_MEDIA).build()

    private val gameAudioAttributes: AudioAttributes =
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).build()

    private fun List<HapticStep>.scaled(intensity: Float) = map { step ->
        if (step is HapticStep.Pulse) step.copy(level = (step.level * intensity).coerceIn(0f, 1f)) else step
    }

    private companion object {
        const val MAX_AMPLITUDE = 255

        // Weaker vibration isn't felt
        const val MIN_AMPLITUDE = 12
        const val MIN_SCALE = 0.05f

        // A shorter pulse doesn't spin up a simple motor
        const val MIN_ON_MS = 12L

        // Without the amplitude control a pulse of level 0 is this part of its time
        const val ON_OFF_BASE = 0.5f
        const val ASSUMED_PRIMITIVE_MS = 30L
        const val MAX_PULSE_MS = 400L

        // Below the priorities of the events: an event isn't interrupted by vJoy pulses
        const val PULSE_PRIORITY = -1
        const val ROAD_STEPS = 10
        const val ROAD_MIN_LEVEL = 0.1f
    }
}

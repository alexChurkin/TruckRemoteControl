package com.alexchurkin.truckremote.control

import kotlin.math.abs

/**
 * Touch logic of a pedal.
 * Digital mode: pedal is fully pressed while it is touched.
 * Analog mode: touch starts with [START_LEVEL], vertical drag changes the level.
 * Lock (gas only): horizontal swipe while holding keeps the current level after release;
 * the next touch of the pedal unlocks it and continues from the locked level.
 */
class PedalHandler(private val listener: Listener, private val lockDistancePx: Float) {

    interface Listener {
        fun onPedalChanged(pedal: PedalHandler)

        fun onPedalLockChanged(pedal: PedalHandler, locked: Boolean)
    }

    var isAnalog = false
        private set
    private var lockAllowed = false

    private var touched = false
    var isLocked = false
        private set
    private var currentLevel = 0f

    private var downX = 0f
    private var downY = 0f
    private var downLevel = 0f
    private var travelPx = 1f

    val isActive: Boolean
        get() = touched || isLocked

    // From 0 to 1
    val level: Float
        get() = if (isActive) currentLevel else 0f

    fun configure(analog: Boolean, lockAllowed: Boolean) {
        isAnalog = analog
        this.lockAllowed = lockAllowed
        if (!lockAllowed) unlock()
    }

    fun onDown(x: Float, y: Float, viewHeight: Int) {
        val wasLocked = isLocked
        if (isLocked) {
            isLocked = false
            listener.onPedalLockChanged(this, false)
        }
        touched = true
        downX = x
        downY = y
        travelPx = (viewHeight * TRAVEL_HEIGHT_PART).coerceAtLeast(1f)
        downLevel = when {
            !isAnalog -> 1f
            wasLocked -> currentLevel
            else -> START_LEVEL
        }
        currentLevel = downLevel
        listener.onPedalChanged(this)
    }

    fun onMove(x: Float, y: Float) {
        if (!touched || isLocked) return

        if (isAnalog) {
            val newLevel = (downLevel + (downY - y) / travelPx).coerceIn(0f, 1f)
            if (newLevel != currentLevel) {
                currentLevel = newLevel
                listener.onPedalChanged(this)
            }
        }

        if (lockAllowed && currentLevel > 0 && abs(x - downX) > lockDistancePx) {
            isLocked = true
            listener.onPedalLockChanged(this, true)
        }
    }

    fun onUp() {
        if (!touched) return
        touched = false
        if (!isLocked) currentLevel = 0f
        listener.onPedalChanged(this)
    }

    // Releases the pedal completely (including lock)
    fun release() {
        val wasLocked = isLocked
        val wasActive = isActive
        touched = false
        isLocked = false
        currentLevel = 0f
        if (wasLocked) listener.onPedalLockChanged(this, false)
        if (wasActive) listener.onPedalChanged(this)
    }

    fun unlock() {
        if (!isLocked) return
        isLocked = false
        if (!touched) currentLevel = 0f
        listener.onPedalLockChanged(this, false)
        listener.onPedalChanged(this)
    }

    private companion object {
        const val START_LEVEL = 0.5f

        // Part of the pedal area height that changes the level from 0 to 1
        const val TRAVEL_HEIGHT_PART = 0.6f
    }
}

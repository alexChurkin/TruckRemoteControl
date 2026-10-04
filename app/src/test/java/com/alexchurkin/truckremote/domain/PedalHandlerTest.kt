package com.alexchurkin.truckremote.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PedalHandlerTest {

    private val lockChanges = mutableListOf<Boolean>()
    private val listener = object : PedalHandler.Listener {
        override fun onPedalChanged(pedal: PedalHandler) = Unit

        override fun onPedalLockChanged(pedal: PedalHandler, locked: Boolean) {
            lockChanges += locked
        }
    }
    private lateinit var pedal: PedalHandler

    @Before
    fun setUp() {
        pedal = PedalHandler(listener, lockDistancePx = 100f)
    }

    @Test
    fun `digital pedal is fully pressed while touched`() {
        pedal.configure(analog = false, lockAllowed = false)
        pedal.onDown(0f, 500f, viewHeight = 1000)
        assertEquals(1f, pedal.level)
        pedal.onMove(0f, 0f)
        assertEquals(1f, pedal.level)
        pedal.onUp()
        assertEquals(0f, pedal.level)
        assertFalse(pedal.isActive)
    }

    @Test
    fun `analog level follows vertical drag and is clamped`() {
        pedal.configure(analog = true, lockAllowed = false)
        pedal.onDown(0f, 500f, viewHeight = 1000)
        assertEquals(0.5f, pedal.level, DELTA)
        // 120 px of 600 px travel
        pedal.onMove(0f, 380f)
        assertEquals(0.7f, pedal.level, DELTA)
        pedal.onMove(0f, 2000f)
        assertEquals(0f, pedal.level, DELTA)
        pedal.onMove(0f, -2000f)
        assertEquals(1f, pedal.level, DELTA)
    }

    @Test
    fun `sideways swipe locks the level until the next touch`() {
        pedal.configure(analog = true, lockAllowed = true)
        pedal.onDown(500f, 500f, viewHeight = 1000)
        pedal.onMove(500f, 440f)
        pedal.onMove(560f, 440f)
        assertFalse("Small move mustn't lock", pedal.isLocked)

        pedal.onMove(620f, 440f)
        assertTrue(pedal.isLocked)
        assertEquals(0.6f, pedal.level, DELTA)
        pedal.onMove(620f, 0f)
        assertEquals("Level is fixed while locked", 0.6f, pedal.level, DELTA)
        pedal.onUp()
        assertEquals("Level is kept after release", 0.6f, pedal.level, DELTA)

        pedal.onDown(100f, 700f, viewHeight = 1000)
        assertFalse(pedal.isLocked)
        assertEquals("Continues from the locked level", 0.6f, pedal.level, DELTA)
        assertEquals(listOf(true, false), lockChanges)
    }

    @Test
    fun `digital lock keeps full press`() {
        pedal.configure(analog = false, lockAllowed = true)
        pedal.onDown(0f, 0f, viewHeight = 1000)
        pedal.onMove(150f, 0f)
        pedal.onUp()
        assertTrue(pedal.isActive)
        assertEquals(1f, pedal.level)
    }

    @Test
    fun `unlock keeps the touch, release resets everything`() {
        pedal.configure(analog = true, lockAllowed = true)
        pedal.onDown(0f, 500f, viewHeight = 1000)
        pedal.onMove(200f, 500f)
        pedal.unlock()
        assertTrue(pedal.isActive)
        assertFalse(pedal.isLocked)

        pedal.onMove(400f, 500f)
        pedal.release()
        assertFalse(pedal.isActive)
        assertFalse(pedal.isLocked)
        assertEquals(0f, pedal.level)
    }

    @Test
    fun `lock can be disabled`() {
        pedal.configure(analog = true, lockAllowed = false)
        pedal.onDown(0f, 500f, viewHeight = 1000)
        pedal.onMove(500f, 500f)
        assertFalse(pedal.isLocked)
    }

    private companion object {
        const val DELTA = 0.0001f
    }
}

package com.alexchurkin.truckremote.data.controller

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BinaryProtocolTest {

    private fun ByteArray.little() = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)

    @Test
    fun `state without actions is 16 bytes`() {
        val message = BinaryProtocol.encodeState(ControllerState(), sequence = 7)

        assertEquals(16, message.size)
        assertEquals(0x02.toByte(), message[0])
        assertEquals(7, message.little().getInt(1))
        assertEquals(0, message[15].toInt())
    }

    @Test
    fun `fields are packed without losing precision`() {
        val state = ControllerState(
            steering = -1.2345678f,
            brakePressed = true,
            cruiseClick = true,
            horn = HornState.Pneumatic,
            gasLevel = 0.6f,
            brakeLevel = 1f,
        )
        val message = BinaryProtocol.encodeState(state, sequence = 0x1_0000_0005).little()

        // The counter wraps around (the server compares numbers in its window)
        assertEquals(5, message.getInt(1))
        assertEquals(-1.2345678f, message.getFloat(5))
        val flags = message.getShort(9).toInt() and 0xFFFF
        assertEquals(1 or (1 shl 7) or (1 shl 8) or (2 shl 9), flags)
        // 1/65535 steps are finer than the 15-bit joystick axes
        assertEquals(0.6f, (message.getShort(11).toInt() and 0xFFFF) / 65535f, 1f / 65535)
        assertEquals(0xFFFF, message.getShort(13).toInt() and 0xFFFF)
    }

    @Test
    fun `clicked and held actions are sent by their codes`() {
        val state = ControllerState()
            .withClick(ControllerAction.Engine)
            .withClick(ControllerAction.Map)
            .withClick(ControllerAction.Map)
            .withHeld(ControllerAction.EngineBrake, true)
        val message = BinaryProtocol.encodeState(state, sequence = 1)

        assertEquals(16 + 3 * 2, message.size)
        assertEquals(3, message[15].toInt())
        assertArrayEquals(byteArrayOf(1, 1, 19, 2, 11, 1), message.copyOfRange(16, 22))
    }

    @Test
    fun `click counter is sent mod 256`() {
        var state = ControllerState()
        repeat(257) { state = state.withClick(ControllerAction.Wipers) }

        assertArrayEquals(byteArrayOf(5, 1), BinaryProtocol.encodeState(state, sequence = 1).copyOfRange(16, 18))
    }

    @Test
    fun `released hold action isn't sent`() {
        val state = ControllerState()
            .withHeld(ControllerAction.EngineBrake, true)
            .withHeld(ControllerAction.EngineBrake, false)

        assertEquals(16, BinaryProtocol.encodeState(state, sequence = 1).size)
    }

    @Test
    fun `action codes are unique and fit a byte`() {
        val codes = ControllerAction.entries.map { it.code }
        assertEquals(codes.size, codes.toSet().size)
        assertTrue(codes.all { it in 1..255 })
    }

    @Test
    fun `server state is decoded`() {
        val message = ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN)
            .put(0x02)
            .putInt(-2)
            .putShort((1 or (1 shl 3) or (1 shl 4) or (1 shl 7) or (3 shl 8)).toShort())
            .putShort(40_000.toShort())
            .array()

        val state = BinaryProtocol.decodeServerState(message, message.size)!!

        assertTrue(state.engineOn)
        assertFalse(state.parkingBrake)
        assertTrue(state.rightBlinker)
        assertTrue(state.trailerAttached)
        assertFalse(state.wipersOn)
        assertTrue(state.analogPedalsAvailable)
        assertEquals(3, state.lightsMode)
        assertEquals(40_000L, state.ffbDurationMs)
        assertEquals(0xFFFF_FFFEL, state.sequence)
    }

    @Test
    fun `text and short messages aren't binary states`() {
        val text = "True,False,False,False,1,0".toByteArray()
        assertFalse(BinaryProtocol.isBinary(text, text.size))
        assertEquals(1, ControllerProtocol.decodeServerMessage(text)!!.lightsMode)
        assertNull(BinaryProtocol.decodeServerState(byteArrayOf(0x02, 1, 2), 3))
    }
}

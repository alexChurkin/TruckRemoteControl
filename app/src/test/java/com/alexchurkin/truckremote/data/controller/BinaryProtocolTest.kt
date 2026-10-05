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
    fun `dashboard is decoded while telemetry is available`() {
        fun message(telemetry: Boolean) = ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN)
            .put(0x02)
            .putInt(1)
            .putShort((if (telemetry) 1 shl 10 else 0).toShort())
            .putShort(0)
            .putShort((-250).toShort())
            .putShort(2459)
            .putShort(2222)
            .put((-1).toByte())
            .putShort(1500)
            .putShort(2500)
            .put(38)
            .put(2)
            .array()

        val dashboard = BinaryProtocol.decodeServerState(message(true), 22)!!.dashboard!!

        assertEquals(-2.5f, dashboard.speed, 0.001f)
        assertEquals(24.59f, dashboard.speedLimit, 0.001f)
        assertEquals(22.22f, dashboard.cruiseSpeed, 0.001f)
        assertEquals(-1, dashboard.gear)
        assertEquals(1500, dashboard.engineRpm)
        assertEquals(2500, dashboard.engineRpmMax)
        assertEquals(38, dashboard.fuelPercent)
        assertTrue(dashboard.imperial)
        assertNull(BinaryProtocol.decodeServerState(message(false), 22)!!.dashboard)
        // A state of an earlier server version has no dashboard
        assertNull(BinaryProtocol.decodeServerState(message(true), 9)!!.dashboard)
    }

    @Test
    fun `extended state carries warnings, axles, retarder and the route`() {
        val message = ByteBuffer.allocate(37).order(ByteOrder.LITTLE_ENDIAN)
            .put(0x02)
            .putInt(1)
            .putShort((1 shl 10).toShort())
            .put(ByteArray(15))
            .putShort((1 or (1 shl 3) or (1 shl 6) or (1 shl 7) or (1 shl 9)).toShort())
            .put(2)
            .put(4)
            .put(23)
            .putShort((-40).toShort())
            .putInt(128_401)
            .putInt(6300)
            .array()

        val state = BinaryProtocol.decodeServerState(message, message.size)!!
        val dashboard = state.dashboard!!

        assertEquals(
            setOf(TruckWarning.AirPressure, TruckWarning.WaterTemperature, TruckWarning.Fuel),
            dashboard.warnings,
        )
        assertEquals(23, dashboard.wearPercent)
        assertEquals(-40, dashboard.restStopMinutes)
        assertEquals(128_401f, dashboard.routeDistance)
        assertEquals(6300L, dashboard.routeTimeSeconds)
        assertTrue(state.differentialLock)
        assertFalse(state.liftAxle)
        assertTrue(state.engineBrake)
        assertEquals(2, state.retarderLevel)
        assertEquals(4, state.retarderSteps)
        // A 22-byte state has no warnings: low fuel is guessed by the level then
        assertNull(BinaryProtocol.decodeServerState(message, 22)!!.dashboard!!.warnings)
    }

    @Test
    fun `text and short messages aren't binary states`() {
        val text = "True,False,False,False,1,0".toByteArray()
        assertFalse(BinaryProtocol.isBinary(text, text.size))
        assertEquals(1, ControllerProtocol.decodeServerMessage(text)!!.lightsMode)
        assertNull(BinaryProtocol.decodeServerState(byteArrayOf(0x02, 1, 2), 3))
    }
}

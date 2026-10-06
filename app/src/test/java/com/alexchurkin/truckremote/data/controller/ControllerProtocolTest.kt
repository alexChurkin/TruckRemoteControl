package com.alexchurkin.truckremote.data.controller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ControllerProtocolTest {

    @Test
    fun `default state is encoded in server field order`() {
        assertEquals(
            "0.0,false,false,false,false,false,false,false,0,false,0.000,0.000,#7",
            ControllerProtocol.encode(ControllerState(), sequence = 7),
        )
    }

    @Test
    fun `pedal levels are appended to the text state, actions aren't`() {
        val state = ControllerState(
            steering = -1.5f,
            gasPressed = true,
            horn = HornState.Pneumatic,
            gasLevel = 0.6f,
            brakeLevel = 0.25f,
        ).withClick(ControllerAction.Engine)
        val parts = ControllerProtocol.encode(state, sequence = 1).split(',')

        assertEquals("-1.5", parts[0])
        assertEquals("true", parts[2])
        assertEquals("2", parts[8])
        assertEquals("0.600", parts[10])
        assertEquals("0.250", parts[11])
        // Sequence is the last field: old servers read only the fields they know
        assertEquals(listOf("#1"), parts.drop(12))
    }

    @Test
    fun `old server message is decoded with defaults for new fields`() {
        val state = ControllerProtocol.decodeServerMessage("True,False,True,False,2,150")!!

        assertTrue(state.engineOn)
        assertFalse(state.parkingBrake)
        assertTrue(state.leftBlinker)
        assertFalse(state.rightBlinker)
        assertEquals(2, state.lightsMode)
        assertEquals(150L, state.ffbDurationMs)
        assertFalse(state.trailerAttached)
        assertFalse(state.analogPedalsAvailable)
    }

    @Test
    fun `new server message contains vehicle state`() {
        val state = ControllerProtocol.decodeServerMessage("False,True,False,False,0,0,1,0,1,1")!!

        assertTrue(state.trailerAttached)
        assertFalse(state.wipersOn)
        assertTrue(state.beaconOn)
        assertTrue(state.analogPedalsAvailable)
    }

    @Test
    fun `sequence number of the server is read from the last field`() {
        val state = ControllerProtocol.decodeServerMessage("True,False,False,False,1,0,0,0,0,1,#4242")!!

        assertEquals(4242L, state.sequence)
        assertTrue(state.analogPedalsAvailable)
        assertNull(ControllerProtocol.decodeServerMessage("True,False,False,False,1,0")!!.sequence)
        // Sequence alone doesn't make a message
        assertNull(ControllerProtocol.decodeServerMessage("True,False,False,False,1,#5"))
    }

    @Test
    fun `malformed messages are ignored`() {
        assertNull(ControllerProtocol.decodeServerMessage("Hi!"))
        assertNull(ControllerProtocol.decodeServerMessage("True,False,True,False,x,0"))
    }
}

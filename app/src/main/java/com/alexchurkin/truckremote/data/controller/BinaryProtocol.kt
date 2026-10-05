package com.alexchurkin.truckremote.data.controller

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Compact binary protocol (version 2) of the server 1.3+, chosen by the hello (see [ControllerProtocol.HELLO]).
 * Little-endian; a binary message starts with its type (a byte below 0x20, text messages are printable).
 *
 * Controller state, 16 bytes + 2 per action:
 * type 0x02 | sequence u32 | steering f32 (as measured) | flags u16 | gas u16 | brake u16 |
 * action count u8 | (action code u8, value u8) * count.
 * Flags: 0 brake, 1 gas, 2 left signal, 3 right signal, 4 emergency, 5 parking brake, 6 lights, 7 cruise
 * (clicks are toggles), 8 pedal levels present, 9-10 horn. Pedal levels are 0..65535 (finer than the 15-bit
 * joystick axes of the server). Actions: a click counter (mod 256) or 1 while a hold action is held.
 * Paused controller: type 0x03, goodbye: type 0x04.
 *
 * Server state, 9 bytes: type 0x02 | sequence u32 | flags u16 | force feedback duration u16 (ms).
 * Flags: 0 engine, 1 parking brake, 2 left blinker, 3 right blinker, 4 trailer, 5 wipers, 6 beacon,
 * 7 analog pedals available, 8-9 lights mode.
 */
object BinaryProtocol {
    const val VERSION = 2

    private const val STATE_TYPE: Byte = 0x02
    private const val PAUSED_TYPE: Byte = 0x03
    private const val GOODBYE_TYPE: Byte = 0x04
    private const val FIRST_TEXT_CHAR = 0x20

    private const val CONTROLLER_HEADER_SIZE = 16
    private const val SERVER_STATE_SIZE = 9
    private const val LEVEL_SCALE = 0xFFFF
    private const val BYTE_MASK = 0xFF
    private const val UINT32_MASK = 0xFFFFFFFFL

    // Bits of the controller flags
    private const val BRAKE_BIT = 0
    private const val GAS_BIT = 1
    private const val LEFT_SIGNAL_BIT = 2
    private const val RIGHT_SIGNAL_BIT = 3
    private const val EMERGENCY_BIT = 4
    private const val PARKING_BRAKE_BIT = 5
    private const val LIGHTS_BIT = 6
    private const val CRUISE_BIT = 7
    private const val PEDAL_LEVELS_BIT = 8
    private const val HORN_SHIFT = 9

    // Bits of the server flags
    private const val ENGINE_BIT = 0
    private const val PARKING_BRAKE_ON_BIT = 1
    private const val LEFT_BLINKER_BIT = 2
    private const val RIGHT_BLINKER_BIT = 3
    private const val TRAILER_BIT = 4
    private const val WIPERS_BIT = 5
    private const val BEACON_BIT = 6
    private const val ANALOG_PEDALS_BIT = 7
    private const val LIGHTS_SHIFT = 8
    private const val TWO_BITS = 0x3
    private const val UINT16_MASK = 0xFFFF

    val PAUSED = byteArrayOf(PAUSED_TYPE)
    val GOODBYE = byteArrayOf(GOODBYE_TYPE)

    fun isBinary(data: ByteArray, length: Int) = length > 0 && (data[0].toInt() and BYTE_MASK) < FIRST_TEXT_CHAR

    fun encodeState(state: ControllerState, sequence: Long): ByteArray {
        // Only clicked and held actions are sent: a missing counter is 0, a missing hold is released
        val actions = state.actionCounters.map { (action, count) -> action.code to (count and BYTE_MASK) } +
            state.heldActions.map { it.code to 1 }
        val flags = flag(state.brakePressed, BRAKE_BIT) or flag(state.gasPressed, GAS_BIT) or
            flag(state.leftSignalClick, LEFT_SIGNAL_BIT) or flag(state.rightSignalClick, RIGHT_SIGNAL_BIT) or
            flag(state.emergencyClick, EMERGENCY_BIT) or flag(state.parkingBrakeClick, PARKING_BRAKE_BIT) or
            flag(state.lightsClick, LIGHTS_BIT) or flag(state.cruiseClick, CRUISE_BIT) or
            flag(true, PEDAL_LEVELS_BIT) or (state.horn.code shl HORN_SHIFT)
        return ByteBuffer.allocate(CONTROLLER_HEADER_SIZE + actions.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            .put(STATE_TYPE)
            .putInt(sequence.toInt())
            .putFloat(state.steering)
            .putShort(flags.toShort())
            .putShort(level(state.gasLevel))
            .putShort(level(state.brakeLevel))
            .put(actions.size.toByte())
            .apply { actions.forEach { (code, value) -> put(code.toByte()).put(value.toByte()) } }
            .array()
    }

    // Returns null if the message is malformed
    fun decodeServerState(data: ByteArray, length: Int): ServerState? {
        if (length < SERVER_STATE_SIZE || data[0] != STATE_TYPE) return null
        val buffer = ByteBuffer.wrap(data, 1, length - 1).order(ByteOrder.LITTLE_ENDIAN)
        val sequence = buffer.getInt().toLong() and UINT32_MASK
        val flags = buffer.getShort().toInt() and UINT16_MASK
        val ffbDurationMs = buffer.getShort().toLong() and UINT16_MASK.toLong()
        fun bit(index: Int) = flags and (1 shl index) != 0
        return ServerState(
            engineOn = bit(ENGINE_BIT),
            parkingBrake = bit(PARKING_BRAKE_ON_BIT),
            leftBlinker = bit(LEFT_BLINKER_BIT),
            rightBlinker = bit(RIGHT_BLINKER_BIT),
            lightsMode = (flags shr LIGHTS_SHIFT) and TWO_BITS,
            ffbDurationMs = ffbDurationMs,
            trailerAttached = bit(TRAILER_BIT),
            wipersOn = bit(WIPERS_BIT),
            beaconOn = bit(BEACON_BIT),
            analogPedalsAvailable = bit(ANALOG_PEDALS_BIT),
            sequence = sequence,
        )
    }

    private fun flag(value: Boolean, bit: Int) = if (value) 1 shl bit else 0

    private fun level(value: Float) = (value.coerceIn(0f, 1f) * LEVEL_SCALE).roundToInt().toShort()
}

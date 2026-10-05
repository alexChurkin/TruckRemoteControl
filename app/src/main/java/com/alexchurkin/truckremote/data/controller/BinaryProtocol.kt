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
 * Server state, 22 bytes (37, 38 and 40 in the extended state): type 0x02 | sequence u32 | flags u16 |
 * force feedback duration u16 (ms) | speed i16 (cm/s) | speed limit u16 (cm/s) | cruise speed u16 (cm/s) |
 * gear i8 | rpm u16 | max rpm u16 | fuel u8 (percent) | game u8 (1 - ETS2, 2 - ATS) |
 * flags2 u16 | retarder level u8 | retarder steps u8 | wear u8 (percent) | rest stop i16 (game minutes) |
 * route distance u32 (m) | route time u32 (s) | server revision u8 (a 22-byte state is revision 1, 37 bytes - 2) |
 * fuel range u16 (km, revision 4+).
 * Flags: 0 engine, 1 parking brake, 2 left blinker, 3 right blinker, 4 trailer, 5 wipers, 6 beacon,
 * 7 analog pedals available, 8-9 lights mode, 10 telemetry available (the dashboard values are real).
 * Flags2: 0-6 warnings (see [TruckWarning], in its order), 7 differential lock, 8 lift axle, 9 engine brake.
 * Job, sent once a second by revision 3+: type 0x05 | delivery minutes left i32 | cargo length u8 | cargo UTF-8 |
 * destination city length u8 | destination city UTF-8; no cargo - no job.
 */
object BinaryProtocol {
    const val VERSION = 2

    // The server revision this app makes use of entirely (an older server is worth updating)
    const val REVISION = 4

    private const val STATE_TYPE: Byte = 0x02
    private const val PAUSED_TYPE: Byte = 0x03
    private const val GOODBYE_TYPE: Byte = 0x04
    private const val JOB_TYPE: Byte = 0x05
    private const val JOB_HEADER_SIZE = 5
    private const val FIRST_TEXT_CHAR = 0x20

    private const val CONTROLLER_HEADER_SIZE = 16
    private const val SERVER_STATE_SIZE = 9
    private const val SERVER_DASHBOARD_SIZE = 22
    private const val SERVER_EXTENDED_SIZE = 37
    private const val FLAGS2_OFFSET = 22
    private const val RETARDER_LEVEL_OFFSET = 24
    private const val RETARDER_STEPS_OFFSET = 25
    private const val WEAR_OFFSET = 26
    private const val REST_STOP_OFFSET = 27
    private const val ROUTE_DISTANCE_OFFSET = 29
    private const val ROUTE_TIME_OFFSET = 33
    private const val REVISION_OFFSET = 37
    private const val FUEL_RANGE_OFFSET = 38
    private const val FUEL_RANGE_END = 40
    private const val REVISION_BASIC = 1
    private const val REVISION_EXTENDED = 2
    private const val DIFFERENTIAL_LOCK_BIT = 7
    private const val LIFT_AXLE_BIT = 8
    private const val ENGINE_BRAKE_BIT = 9
    private const val CENTIMETERS_IN_METER = 100f
    private const val GAME_ATS = 2
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
    private const val TELEMETRY_BIT = 10
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
        val extended = length >= SERVER_EXTENDED_SIZE
        val all = ByteBuffer.wrap(data, 0, length).order(ByteOrder.LITTLE_ENDIAN)
        val flags2 = if (extended) all.getShort(FLAGS2_OFFSET).toInt() and UINT16_MASK else 0
        fun bit2(index: Int) = flags2 and (1 shl index) != 0
        val dashboard = if (length >= SERVER_DASHBOARD_SIZE && bit(TELEMETRY_BIT)) decodeDashboard(buffer) else null
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
            dashboard = if (dashboard != null && extended) withExtras(dashboard, all, flags2, length) else dashboard,
            differentialLock = bit2(DIFFERENTIAL_LOCK_BIT),
            liftAxle = bit2(LIFT_AXLE_BIT),
            engineBrake = bit2(ENGINE_BRAKE_BIT),
            retarderLevel = if (extended) all.get(RETARDER_LEVEL_OFFSET).toInt() and BYTE_MASK else 0,
            retarderSteps = if (extended) all.get(RETARDER_STEPS_OFFSET).toInt() and BYTE_MASK else 0,
            serverRevision = revision(all, length),
        )
    }

    // The extended part of the state: the whole message in the buffer
    private fun withExtras(dashboard: Dashboard, all: ByteBuffer, flags2: Int, length: Int) = dashboard.copy(
        warnings = TruckWarning.entries.filter { flags2 and (1 shl it.ordinal) != 0 }.toSet(),
        wearPercent = all.get(WEAR_OFFSET).toInt() and BYTE_MASK,
        restStopMinutes = all.getShort(REST_STOP_OFFSET).toInt(),
        routeDistance = (all.getInt(ROUTE_DISTANCE_OFFSET).toLong() and UINT32_MASK).toFloat(),
        routeTimeSeconds = all.getInt(ROUTE_TIME_OFFSET).toLong() and UINT32_MASK,
        fuelRangeKm = if (length >= FUEL_RANGE_END) all.getShort(FUEL_RANGE_OFFSET).toInt() and UINT16_MASK else 0,
    )

    private fun revision(all: ByteBuffer, length: Int) = when {
        length > REVISION_OFFSET -> all.get(REVISION_OFFSET).toInt() and BYTE_MASK
        length >= SERVER_EXTENDED_SIZE -> REVISION_EXTENDED
        else -> REVISION_BASIC
    }

    fun isJob(data: ByteArray, length: Int) = length >= JOB_HEADER_SIZE && data[0] == JOB_TYPE

    // null without a job (or if the message is malformed)
    fun decodeJob(data: ByteArray, length: Int): Job? {
        val buffer = ByteBuffer.wrap(data, 1, length - 1).order(ByteOrder.LITTLE_ENDIAN)
        val minutesLeft = buffer.getInt()
        fun text(): String? {
            if (!buffer.hasRemaining()) return null
            val size = buffer.get().toInt() and BYTE_MASK
            if (buffer.remaining() < size) return null
            return String(data, buffer.position(), size, Charsets.UTF_8).also {
                buffer.position(buffer.position() + size)
            }
        }
        val cargo = text()?.takeIf { it.isNotEmpty() } ?: return null
        val city = text() ?: return null
        return Job(cargo, city, minutesLeft)
    }

    // The buffer is after the force feedback duration
    private fun decodeDashboard(buffer: ByteBuffer) = Dashboard(
        speed = buffer.getShort() / CENTIMETERS_IN_METER,
        speedLimit = (buffer.getShort().toInt() and UINT16_MASK) / CENTIMETERS_IN_METER,
        cruiseSpeed = (buffer.getShort().toInt() and UINT16_MASK) / CENTIMETERS_IN_METER,
        gear = buffer.get().toInt(),
        engineRpm = buffer.getShort().toInt() and UINT16_MASK,
        engineRpmMax = buffer.getShort().toInt() and UINT16_MASK,
        fuelPercent = buffer.get().toInt() and BYTE_MASK,
        isAts = (buffer.get().toInt() and BYTE_MASK) == GAME_ATS,
    )

    private fun flag(value: Boolean, bit: Int) = if (value) 1 shl bit else 0

    private fun level(value: Float) = (value.coerceIn(0f, 1f) * LEVEL_SCALE).roundToInt().toShort()
}

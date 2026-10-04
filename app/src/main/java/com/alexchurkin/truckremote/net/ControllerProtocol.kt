package com.alexchurkin.truckremote.net

import java.util.Locale

// Additional actions; their order must match the order on the server
enum class ControllerAction {
    Engine,
    Trailer,
    Activate,
    Wipers,
    DiffLock,
    LiftAxle,
    Beacon,
    LightHorn,
}

enum class HornState(val code: Int) {
    Off(0),
    Horn(1),
    Pneumatic(2),
}

/**
 * Controller state sent to the server many times per second.
 * "Click" values are toggled on every click, so a lost packet can't lose a click.
 */
data class ControllerState(
    val steering: Float = 0f,
    val brakePressed: Boolean = false,
    val gasPressed: Boolean = false,
    val leftSignalClick: Boolean = false,
    val rightSignalClick: Boolean = false,
    val emergencyClick: Boolean = false,
    val parkingBrakeClick: Boolean = false,
    val lightsClick: Boolean = false,
    val horn: HornState = HornState.Off,
    val cruiseClick: Boolean = false,
    // From 0 to 1, used by analog pedals
    val gasLevel: Float = 0f,
    val brakeLevel: Float = 0f,
    // Click counters in ControllerAction order
    val actionCounters: List<Int> = List(ControllerAction.entries.size) { 0 },
)

data class ServerState(
    val engineOn: Boolean,
    val parkingBrake: Boolean,
    val leftBlinker: Boolean,
    val rightBlinker: Boolean,
    // 0 - off, 1 - parking lights, 2 - low beam, 3 - high beam
    val lightsMode: Int,
    val ffbDurationMs: Long,
    // Sent by server 1.3+
    val trailerAttached: Boolean = false,
    val wipersOn: Boolean = false,
    val beaconOn: Boolean = false,
    val analogPedalsAvailable: Boolean = false,
    // Number of the message, sent by server 1.4+ (lets the controller measure packet loss)
    val sequence: Long? = null,
)

object ControllerProtocol {
    const val HELLO = "TruckRemoteHello"
    const val HELLO_ANSWER = "Hi!"
    const val PAUSED = "paused"
    const val GOODBYE = "goodbye"

    private const val SERVER_BASE_FIELDS = 6

    /*
     * Messages of both sides may end with a tagged sequence number ("#123").
     * It is the last field, so previous versions (which read fields by their positions) ignore it.
     * The server drops controller messages that came out of order (UDP may reorder packets),
     * otherwise an old toggle value would make an extra click.
     */
    private const val SEQUENCE_TAG = '#'

    fun encode(state: ControllerState, sequence: Long): String = buildString(capacity = 136) {
        append(state.steering).append(',')
        append(state.brakePressed).append(',')
        append(state.gasPressed).append(',')
        append(state.leftSignalClick).append(',')
        append(state.rightSignalClick).append(',')
        append(state.emergencyClick).append(',')
        append(state.parkingBrakeClick).append(',')
        append(state.lightsClick).append(',')
        append(state.horn.code).append(',')
        append(state.cruiseClick).append(',')
        append(String.format(Locale.ROOT, "%.3f,%.3f", state.gasLevel, state.brakeLevel))
        state.actionCounters.forEach { append(',').append(it) }
        append(',').append(SEQUENCE_TAG).append(sequence)
    }

    // Returns null if the message is malformed
    fun decodeServerMessage(message: String): ServerState? {
        val allParts = message.trim().split(',')
        val sequence = allParts.last().takeIf { it.startsWith(SEQUENCE_TAG) }?.drop(1)?.toLongOrNull()
        val parts = if (sequence != null) allParts.dropLast(1) else allParts
        if (parts.size < SERVER_BASE_FIELDS) return null

        fun flag(index: Int) = parts.getOrNull(index) == "1"

        return ServerState(
            engineOn = parts[0].toBooleanStrictIgnoreCase(),
            parkingBrake = parts[1].toBooleanStrictIgnoreCase(),
            leftBlinker = parts[2].toBooleanStrictIgnoreCase(),
            rightBlinker = parts[3].toBooleanStrictIgnoreCase(),
            lightsMode = parts[4].toIntOrNull() ?: return null,
            ffbDurationMs = parts[5].toLongOrNull() ?: return null,
            trailerAttached = flag(6),
            wipersOn = flag(7),
            beaconOn = flag(8),
            analogPedalsAvailable = flag(9),
            sequence = sequence,
        )
    }

    // Server is written in C#, it sends "True"/"False"
    private fun String.toBooleanStrictIgnoreCase() = equals("true", ignoreCase = true)
}

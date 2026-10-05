package com.alexchurkin.truckremote.data.controller

import java.util.Locale

/**
 * Actions of the panel. They are sent by their fixed [code] (not by the place of a button), so buttons
 * can be placed anywhere; the server presses the game key of a code it knows and skips the others.
 * A click action is sent as a click counter, a [isHold] action as held (1) while its button is pressed.
 */
enum class ControllerAction(val code: Int, val isHold: Boolean = false) {
    Engine(1),
    Trailer(2),
    Activate(3),
    LightHorn(4),
    Wipers(5),
    Beacon(6),
    DiffLock(7),
    LiftAxle(8),
    RetarderUp(9),
    RetarderDown(10),
    EngineBrake(11, isHold = true),
    CruiseUp(12),
    CruiseDown(13),
    CruiseResume(14),
    QuickPark(15),
    CameraInterior(16),
    CameraChase(17),
    CameraCycle(18),
    Map(19),
    Display(20),
    Hud(21),
    RadioNext(22),
    QuickSave(23),
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
    // Clicks of every click action (a counter can't lose a click in a lost packet)
    val actionCounters: Map<ControllerAction, Int> = emptyMap(),
    // Hold actions whose buttons are pressed now
    val heldActions: Set<ControllerAction> = emptySet(),
) {
    fun withClick(action: ControllerAction) =
        copy(actionCounters = actionCounters + (action to (actionCounters[action] ?: 0) + 1))

    fun withHeld(action: ControllerAction, held: Boolean) =
        copy(heldActions = if (held) heldActions + action else heldActions - action)
}

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
    // Servers before version 2 ignore the version after the hello (they check the beginning) and answer "Hi!":
    // the text protocol is used with them, the binary one (BinaryProtocol) with the others
    const val HELLO = "TruckRemoteHello" + BinaryProtocol.VERSION
    const val HELLO_ANSWER = "Hi!"
    const val BINARY_HELLO_ANSWER = HELLO_ANSWER + BinaryProtocol.VERSION
    const val PAUSED = "paused"
    const val GOODBYE = "goodbye"

    // Fields of a server message; the base ones are sent by every server version
    private const val FIELD_ENGINE = 0
    private const val FIELD_PARKING_BRAKE = 1
    private const val FIELD_LEFT_BLINKER = 2
    private const val FIELD_RIGHT_BLINKER = 3
    private const val FIELD_LIGHTS_MODE = 4
    private const val FIELD_FFB_DURATION = 5
    private const val SERVER_BASE_FIELDS = 6
    private const val FIELD_TRAILER = 6
    private const val FIELD_WIPERS = 7
    private const val FIELD_BEACON = 8
    private const val FIELD_ANALOG_PEDALS = 9

    /*
     * Messages of both sides may end with a tagged sequence number ("#123").
     * It is the last field, so previous versions (which read fields by their positions) ignore it.
     * The server drops controller messages that came out of order (UDP may reorder packets),
     * otherwise an old toggle value would make an extra click.
     */
    private const val SEQUENCE_TAG = '#'

    // Text state for servers before version 2 (they don't know the actions of the panel)
    fun encode(state: ControllerState, sequence: Long): String = buildString(capacity = 96) {
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
        append(',').append(SEQUENCE_TAG).append(sequence)
    }

    // Text or binary message of the server; returns null if the message is malformed
    fun decodeServerMessage(data: ByteArray, length: Int = data.size): ServerState? =
        if (BinaryProtocol.isBinary(data, length)) {
            BinaryProtocol.decodeServerState(data, length)
        } else {
            decodeServerMessage(String(data, 0, length))
        }

    // Returns null if the message is malformed
    fun decodeServerMessage(message: String): ServerState? {
        val allParts = message.trim().split(',')
        val sequence = allParts.last().takeIf { it.startsWith(SEQUENCE_TAG) }?.drop(1)?.toLongOrNull()
        val parts = if (sequence != null) allParts.dropLast(1) else allParts
        val lightsMode = parts.getOrNull(FIELD_LIGHTS_MODE)?.toIntOrNull()
        val ffbDurationMs = parts.getOrNull(FIELD_FFB_DURATION)?.toLongOrNull()
        if (parts.size < SERVER_BASE_FIELDS || lightsMode == null || ffbDurationMs == null) return null

        fun flag(index: Int) = parts.getOrNull(index) == "1"

        return ServerState(
            engineOn = parts[FIELD_ENGINE].toBooleanStrictIgnoreCase(),
            parkingBrake = parts[FIELD_PARKING_BRAKE].toBooleanStrictIgnoreCase(),
            leftBlinker = parts[FIELD_LEFT_BLINKER].toBooleanStrictIgnoreCase(),
            rightBlinker = parts[FIELD_RIGHT_BLINKER].toBooleanStrictIgnoreCase(),
            lightsMode = lightsMode,
            ffbDurationMs = ffbDurationMs,
            trailerAttached = flag(FIELD_TRAILER),
            wipersOn = flag(FIELD_WIPERS),
            beaconOn = flag(FIELD_BEACON),
            analogPedalsAvailable = flag(FIELD_ANALOG_PEDALS),
            sequence = sequence,
        )
    }

    // Server is written in C#, it sends "True"/"False"
    private fun String.toBooleanStrictIgnoreCase() = equals("true", ignoreCase = true)
}

package com.alexchurkin.truckremote.ui.main

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.alexchurkin.truckremote.R
import com.alexchurkin.truckremote.TruckRemoteApp
import com.alexchurkin.truckremote.data.analytics.Analytics
import com.alexchurkin.truckremote.data.controller.BinaryProtocol
import com.alexchurkin.truckremote.data.controller.ConnectionState
import com.alexchurkin.truckremote.data.controller.ControllerAction
import com.alexchurkin.truckremote.data.controller.ControllerRepository
import com.alexchurkin.truckremote.data.controller.ControllerState
import com.alexchurkin.truckremote.data.controller.Feedback
import com.alexchurkin.truckremote.data.controller.HornState
import com.alexchurkin.truckremote.data.controller.LinkQuality
import com.alexchurkin.truckremote.data.controller.RoadFeel
import com.alexchurkin.truckremote.data.controller.ServerState
import com.alexchurkin.truckremote.data.controller.isConnected
import com.alexchurkin.truckremote.data.device.Haptics
import com.alexchurkin.truckremote.data.device.WifiStatus
import com.alexchurkin.truckremote.data.sensor.TiltReading
import com.alexchurkin.truckremote.data.sensor.TiltSensor
import com.alexchurkin.truckremote.data.settings.ActionLayout
import com.alexchurkin.truckremote.data.settings.AppSettings
import com.alexchurkin.truckremote.data.settings.Game
import com.alexchurkin.truckremote.data.settings.PedalMode
import com.alexchurkin.truckremote.domain.PedalHandler
import com.alexchurkin.truckremote.domain.SteeringCurve
import com.alexchurkin.truckremote.domain.SteeringProcessor
import com.alexchurkin.truckremote.util.isValidIpv4
import kotlin.math.abs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Pedal {
    Brake,
    Gas,
}

data class PedalsUiState(
    // Level bars are shown for analog pedals (and for the locked gas)
    val analog: Boolean = false,
    val brakeLevel: Float = 0f,
    val gasLevel: Float = 0f,
    val gasLocked: Boolean = false,
)

enum class AutoPause {
    FaceDown,
    FaceUp,
    NoSensor,
}

data class MainUiState(
    val connection: ConnectionState = ConnectionState.Disconnected,
    val linkQuality: LinkQuality? = null,
    // null: the truck state is unknown (no connection), nothing is shown as turned on
    val truck: ServerState? = null,
    val pedals: PedalsUiState = PedalsUiState(),
    val pausedByUser: Boolean = false,
    // Nothing is controlled while the phone lies (screen down or up) or the tilt sensor is silent
    val autoPause: AutoPause? = null,
    // Instruments are shown while the server sends them (the game and its telemetry plugin are running)
    val showDashboard: Boolean = true,
    val actionLayout: ActionLayout = ActionLayout.Default,
    // Speed and distances in miles (by the game or the user's choice)
    val imperialUnits: Boolean = false,
) {
    val isConnected: Boolean
        get() = connection.isConnected

    val isPaused: Boolean
        get() = pausedByUser || autoPause != null
}

// One-off events for the screen
sealed interface MainEffect {
    data class Message(@field:StringRes val text: Int, val suffix: String? = null) : MainEffect

    // Once per connection attempt, the search goes on in background
    data object ServerNotFound : MainEffect

    data object ShowAd : MainEffect

    data object ThrottleLockChanged : MainEffect

    // The server on the PC is older than the app: the dashboard, warnings or the job aren't shown
    data object ServerOutdated : MainEffect
}

// What the screen shows on its first start
enum class StartAction {
    None,
    Guide,
    ReleaseNotes,
}

data class SignalInfo(val wifiEnabled: Boolean, val rssi: Int, val linkQuality: LinkQuality?)

/**
 * Logic of the controller screen: connection, steering by tilt, pedals and buttons.
 * The screen renders [state], shows [effects] and calls the event methods.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val controller: ControllerRepository,
    private val settings: AppSettings,
    private val tiltSensor: TiltSensor,
    private val haptics: Haptics,
    private val wifi: WifiStatus,
    private val analytics: Analytics,
) : ViewModel(),
    PedalHandler.Listener {

    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    private val _effects = Channel<MainEffect>(Channel.BUFFERED)
    val effects: Flow<MainEffect> = _effects.receiveAsFlow()

    private val brakePedal = PedalHandler(this, lockDistancePx = Float.MAX_VALUE)
    private val gasPedal = PedalHandler(this, lockDistancePx = Float.MAX_VALUE)

    // The levels of the pedals sent last, and of the released analog pedals their springs are returning
    private val lastLevels = mutableMapOf<PedalHandler, Float>()
    private val springLevels = mutableMapOf<PedalHandler, Float>()
    private var springJob: Job? = null
    private var releasingPedals = false

    // Hold actions whose buttons are pressed now, and the shortest time each of them stays held
    private val pressedActions = mutableSetOf<ControllerAction>()
    private val holdJobs = mutableMapOf<ControllerAction, Job>()
    private val foreground = MutableStateFlow(false)

    // Settings are cached in it: tilt readings come very often
    private val steering = SteeringProcessor()
    private val vibration = MutableStateFlow(VibrationSettings())
    private var analogPedalsMode = false
    private var pneumaticHorn = false

    private var searchingByBroadcast = false
    private var notFoundShown = false
    private var analogUnavailableWarned = false

    // FaceDown or FaceUp while the phone lies
    private var lying: AutoPause? = null
    private var autoPauseEnabled = true
    private var cruiseCheck: Job? = null
    private var started = false

    init {
        applySettings()
        settings.changes().onEach { applySettings() }.launchIn(viewModelScope)
        controller.connectionState.onEach(::onConnectionState).launchIn(viewModelScope)
        controller.truckState.onEach(::onTruckState).launchIn(viewModelScope)
        controller.linkQuality.onEach { quality -> _state.update { it.copy(linkQuality = quality) } }
            .launchIn(viewModelScope)
        controller.feedback.onEach(::onFeedback).launchIn(viewModelScope)
        // The road vibrates while the screen is shown and the truck is controlled
        combine(
            controller.road,
            foreground,
            state.map { it.isConnected && !it.isPaused }.distinctUntilChanged(),
            vibration,
        ) { road, shown, controlled, current ->
            if (shown && controlled && current.roadOn) road to current.intensity else null
        }
            .distinctUntilChanged()
            .onEach { if (it == null) haptics.setRoad(RoadFeel.None, 0f) else haptics.setRoad(it.first, it.second) }
            .launchIn(viewModelScope)

        // The tilt sensor works only while the screen is shown and the server is connected
        combine(foreground, state.map { it.isConnected }.distinctUntilChanged()) { shown, connected ->
            shown && connected
        }
            .distinctUntilChanged()
            .onEach { active -> if (!active) setAutoPause(null) }
            .flatMapLatest { active -> if (active) readingsWithWatchdog() else emptyFlow() }
            .onEach(::onTilt)
            .launchIn(viewModelScope)
    }

    override fun onCleared() {
        haptics.stop()
        controller.disconnect()
    }

    // Events are felt only while the truck is controlled, the small clicks can be turned off
    private fun onFeedback(feedback: Feedback) {
        val current = vibration.value
        if (!current.enabled || !foreground.value || !isControllable) return
        when (feedback) {
            is Feedback.Pulse -> haptics.pulse(feedback.durationMs, current.intensity)

            is Feedback.Event -> if (current.clicks || !feedback.event.isClick) {
                haptics.play(feedback.event, feedback.strength, current.intensity)
            }
        }
    }

    /* Screen lifecycle */

    // Called when the screen is created; connects and tells what to show first. A rotated screen keeps the view model
    // (nothing is done again), a screen restored after the app was killed in background gets a new one and connects
    fun start(releaseNotesVersion: Int): StartAction {
        if (started) return StartAction.None
        started = true
        connect(useSpecifiedServer = settings.useSpecifiedServer)
        return when {
            !settings.guideShown -> {
                settings.guideShown = true
                settings.lastShownReleaseNotes = releaseNotesVersion
                StartAction.Guide
            }

            settings.lastShownReleaseNotes != releaseNotesVersion -> StartAction.ReleaseNotes

            else -> StartAction.None
        }
    }

    fun onReleaseNotesShown(version: Int) {
        settings.lastShownReleaseNotes = version
    }

    // Locked throttle mustn't come back after returning to the app
    fun setForeground(shown: Boolean) {
        foreground.value = shown
        controller.setForeground(shown)
        releasePedals()
    }

    // Horizontal swipe distance on gas which locks the throttle, depends on the screen density
    fun setThrottleLockDistance(px: Float) {
        brakePedal.lockDistancePx = px
        gasPedal.lockDistancePx = px
    }

    /* Connection */

    fun connect(useSpecifiedServer: Boolean) {
        _state.update { it.copy(pausedByUser = false) }
        if (!wifi.isEnabled) send(MainEffect.Message(R.string.no_wifi_conn_detected))
        searchingByBroadcast = !useSpecifiedServer
        notFoundShown = false

        if (!useSpecifiedServer) {
            send(MainEffect.Message(R.string.searching_on_local))
            controller.connect(null, settings.serverPort, knownIp = settings.lastServerIp)
            return
        }
        val serverIp = settings.specifiedServerIp
        if (isValidIpv4(serverIp)) {
            send(MainEffect.Message(R.string.trying_to_connect))
            // If the address doesn't answer (the PC has got another one), the server is searched for as well
            controller.connect(serverIp, settings.serverPort, knownIp = settings.lastServerIp)
        } else {
            send(MainEffect.Message(R.string.def_server_ip_not_correct))
        }
    }

    // The PC has got another address and the server was found by the search: the new address is the specified
    // one from now on, so the next connection doesn't wait for the old one
    private fun rememberSpecifiedServer() {
        val found = controller.serverAddress
        if (searchingByBroadcast || found.isNullOrBlank() || found == settings.specifiedServerIp) return
        settings.specifiedServerIp = found
    }

    fun disconnect() {
        _state.update { it.copy(pausedByUser = false) }
        controller.disconnect()
    }

    fun togglePause() {
        if (!state.value.isConnected) return
        val paused = !state.value.pausedByUser
        if (paused) releasePedals()
        controller.setPausedByUser(paused || state.value.autoPause != null)
        _state.update { it.copy(pausedByUser = paused) }
        if (paused) send(MainEffect.ShowAd)
    }

    fun signalInfo() = SignalInfo(
        wifi.isEnabled,
        wifi.rssi,
        state.value.linkQuality?.takeIf {
            state.value.isConnected
        },
    )

    private fun onConnectionState(connection: ConnectionState) {
        val wasConnected = state.value.isConnected
        _state.update { it.copy(connection = connection) }
        when {
            // Resumed session: nothing has changed for the user
            connection.isConnected && wasConnected -> Unit

            connection.isConnected -> {
                analogUnavailableWarned = false
                send(MainEffect.Message(R.string.connected_to_server_at, suffix = controller.serverAddress))
                settings.lastServerIp = controller.serverAddress
                rememberSpecifiedServer()
                reportConnected()
            }

            else -> {
                if (wasConnected) releasePedals()
                when (connection) {
                    ConnectionState.Lost -> send(MainEffect.Message(R.string.connection_lost))

                    ConnectionState.NotFound -> if (!notFoundShown) {
                        notFoundShown = true
                        send(MainEffect.ServerNotFound)
                    }

                    else -> Unit
                }
            }
        }
    }

    private fun onTruckState(truck: ServerState?) {
        val analogBefore = state.value.truck?.analogPedalsAvailable
        _state.update { it.copy(truck = truck) }
        // The units set in the game come with its state
        val imperial = imperialUnits()
        if (imperial != state.value.imperialUnits) _state.update { it.copy(imperialUnits = imperial) }
        // The settings of the game being played (the dashboard tells the game)
        val game = truck?.dashboard?.let { if (it.isAts) Game.Ats else Game.Ets2 }
        if (game != null && game != settings.lastGame) settings.lastGame = game
        if (truck != null && truck.serverRevision < BinaryProtocol.REVISION &&
            settings.serverUpdateHintRevision < BinaryProtocol.REVISION
        ) {
            settings.serverUpdateHintRevision = BinaryProtocol.REVISION
            send(MainEffect.ServerOutdated)
        }
        // Pedals state should be sent in the right form (axes or keys)
        if (truck != null && truck.analogPedalsAvailable != analogBefore) sendPedalsState()
    }

    private fun reportConnected() = analytics.report(
        Analytics.EVENT_SERVER_CONNECTED,
        mapOf(
            "search" to if (searchingByBroadcast) "broadcast" else "ip",
            "tilt_sensor" to tiltSensor.kind,
            "pedals" to if (analogPedalsMode) "analog" else "digital",
        ),
    )

    /* Buttons */

    private val isControllable: Boolean
        get() = state.value.isConnected && !state.value.isPaused

    // Toggles are flipped on every click, so a lost message can't lose a click
    private fun toggle(transform: (ControllerState) -> ControllerState) {
        if (isControllable) controller.updateState(transform)
    }

    fun onLeftSignal() = toggle { it.copy(leftSignalClick = !it.leftSignalClick) }

    fun onRightSignal() = toggle { it.copy(rightSignalClick = !it.rightSignalClick) }

    fun onEmergencySignal() = toggle { it.copy(emergencyClick = !it.emergencyClick) }

    fun onParkingBrake() = toggle { it.copy(parkingBrakeClick = !it.parkingBrakeClick) }

    fun onLights() = toggle { it.copy(lightsClick = !it.lightsClick) }

    // Returns true if the action was sent (the button gives haptic feedback then)
    fun onAction(action: ControllerAction): Boolean {
        if (!isControllable) return false
        controller.clickAction(action)
        return true
    }

    // A hold action is held while its button is pressed; returns true if it was sent
    fun onActionHold(action: ControllerAction, held: Boolean): Boolean {
        if (held && !isControllable) return false
        // An older server knows only the click of the action
        if ((state.value.truck?.serverRevision ?: 0) < action.holdRevision) {
            if (held) controller.clickAction(action)
            return true
        }
        if (held) {
            pressedActions += action
            controller.setActionHeld(action, true)
            // A short tap is held long enough for the server and the game to see it
            holdJobs[action] = viewModelScope.launch { delay(MIN_HOLD_MS) }
        } else {
            pressedActions -= action
            val pressed = holdJobs[action]
            viewModelScope.launch {
                pressed?.join()
                // Unless it was pressed again meanwhile
                if (action !in pressedActions) controller.setActionHeld(action, false)
            }
        }
        return true
    }

    // The quick actions panel was edited: an action was put into a place or dragged to another one,
    // or the layout was reset
    fun onActionLayoutChange(layout: ActionLayout) {
        gameSettings().actionLayout = layout
    }

    private fun gameSettings() = settings.game(settings.lastGame)

    // As chosen in the settings of the app; "as in the game" is what the game is set to, if the server knows it
    private fun imperialUnits() =
        gameSettings().speedUnits.isImperial(settings.lastGame, state.value.truck?.dashboard?.gameImperialUnits)

    // A double tap on the gas or the cruise button of the dashboard; returns true if it was sent
    fun onCruiseToggle(): Boolean {
        if (!isControllable) return false
        controller.updateState { it.copy(cruiseClick = !it.cruiseClick) }
        checkCruiseEngaged()
        return true
    }

    // The game doesn't turn the cruise control on below a certain speed (and e.g. while braking):
    // that is told instead of doing nothing silently. Known only with the instruments of the truck
    private fun checkCruiseEngaged() {
        cruiseCheck?.cancel()
        val dashboard = state.value.truck?.dashboard ?: return
        // It was on: the click turns it off
        if (dashboard.cruiseSpeed > 0f) return
        cruiseCheck = viewModelScope.launch {
            delay(CRUISE_CHECK_MS)
            val now = state.value.truck?.dashboard ?: return@launch
            if (now.cruiseSpeed > 0f || !isControllable) return@launch
            val slow = abs(now.speed) < CRUISE_MIN_SPEED
            send(MainEffect.Message(if (slow) R.string.cruise_not_engaged_slow else R.string.cruise_not_engaged))
        }
    }

    // Returns true if the horn works now (the button is animated then)
    fun onHorn(pressed: Boolean): Boolean {
        if (!state.value.isConnected) return false
        val horn = when {
            !pressed -> HornState.Off
            pneumaticHorn -> HornState.Pneumatic
            else -> HornState.Horn
        }
        controller.updateState { it.copy(horn = horn) }
        return true
    }

    /* Pedals */

    fun onPedalDown(pedal: Pedal, x: Float, y: Float, height: Int) {
        // Braking always releases the locked throttle
        if (pedal == Pedal.Brake) gasPedal.unlock()
        handler(pedal).onDown(x, y, height)
        warnIfAnalogUnavailable()
    }

    fun onPedalMove(pedal: Pedal, x: Float, y: Float) = handler(pedal).onMove(x, y)

    fun onPedalUp(pedal: Pedal) = handler(pedal).onUp()

    override fun onPedalChanged(pedal: PedalHandler) {
        // A released analog pedal goes back by its spring, as a real one, not at once
        val last = lastLevels[pedal] ?: 0f
        val released = !pedal.isActive && last > 0f
        if (analogPedalsMode && !releasingPedals && released) startSpring(pedal, last)
        sendPedalsState()
        showPedals()
    }

    private fun startSpring(pedal: PedalHandler, from: Float) {
        springLevels[pedal] = from
        if (springJob?.isActive == true) return
        springJob = viewModelScope.launch {
            while (springLevels.isNotEmpty()) {
                delay(SPRING_STEP_MS)
                springLevels.keys.toList().forEach { returning ->
                    val level = (springLevels[returning] ?: 0f) - SPRING_STEP_MS.toFloat() / SPRING_RETURN_MS
                    // Pressed again harder than it has returned: the finger holds the pedal now
                    if (level <= 0f || returning.level >= level) {
                        springLevels.remove(returning)
                    } else {
                        springLevels[returning] = level
                    }
                }
                sendPedalsState()
                showPedals()
            }
        }
    }

    // Where the pedal is: under the finger, or on its way back
    private fun level(pedal: PedalHandler) = maxOf(pedal.level, springLevels[pedal] ?: 0f)

    override fun onPedalLockChanged(pedal: PedalHandler, locked: Boolean) {
        send(MainEffect.ThrottleLockChanged)
        showPedals()
    }

    private fun handler(pedal: Pedal) = if (pedal == Pedal.Brake) brakePedal else gasPedal

    // At once, without the spring: the controls are stopped (a pause, a lost connection, the app is left)
    private fun releasePedals() {
        releasingPedals = true
        springLevels.clear()
        brakePedal.release()
        gasPedal.release()
        releasingPedals = false
        sendPedalsState()
        showPedals()
    }

    // Analog levels are used when the server supports them, keys otherwise
    private fun sendPedalsState() {
        val analog = analogPedalsMode && state.value.truck?.analogPedalsAvailable == true
        val brakeLevel = level(brakePedal)
        val gasLevel = level(gasPedal)
        lastLevels[brakePedal] = brakeLevel
        lastLevels[gasPedal] = gasLevel
        controller.updateState {
            it.copy(
                // Without analog axes the key is pressed while the pedal is touched (an analog touch starts from 0)
                brakePressed = !analog && brakePedal.isActive,
                gasPressed = !analog && gasPedal.isActive,
                brakeLevel = if (analog) brakeLevel else 0f,
                gasLevel = if (analog) gasLevel else 0f,
            )
        }
    }

    private fun showPedals() = _state.update {
        it.copy(
            pedals = PedalsUiState(
                analog = analogPedalsMode,
                brakeLevel = level(brakePedal),
                gasLevel = level(gasPedal),
                gasLocked = gasPedal.isLocked,
            ),
        )
    }

    private fun warnIfAnalogUnavailable() {
        val truck = state.value.truck ?: return
        if (analogPedalsMode && !analogUnavailableWarned && !truck.analogPedalsAvailable) {
            analogUnavailableWarned = true
            send(MainEffect.Message(R.string.analog_pedals_unavailable))
        }
    }

    /* Steering */

    // null after the sensor has been silent for SENSOR_TIMEOUT_MS
    private fun readingsWithWatchdog(): Flow<TiltReading?> = tiltSensor.readings().transformLatest { reading ->
        emit(reading)
        delay(SENSOR_TIMEOUT_MS)
        emit(null)
    }

    private fun onTilt(reading: TiltReading?) {
        if (reading == null) {
            setAutoPause(AutoPause.NoSensor)
            return
        }
        // Hysteresis: a phone held almost flat doesn't flicker between the states
        lying = when {
            !autoPauseEnabled -> null

            reading.screenUp < (if (lying == AutoPause.FaceDown) FACE_DOWN_EXIT else FACE_DOWN_ENTER) ->
                AutoPause.FaceDown

            reading.screenUp > (if (lying == AutoPause.FaceUp) FACE_UP_EXIT else FACE_UP_ENTER) ->
                AutoPause.FaceUp

            else -> null
        }
        setAutoPause(lying)
        val value = steering.process(reading.angle, reading.timeNanos)
        controller.updateState { it.copy(steering = value) }
    }

    // The server releases everything while the controller is paused; the pedals are released here too
    private fun setAutoPause(reason: AutoPause?) {
        val previous = state.value.autoPause
        if (reason == previous) return
        if (reason == null) lying = null
        _state.update { it.copy(autoPause = reason) }
        // Also without a connection: the pause mustn't stay in the client after the connection comes back
        controller.setPausedByUser(reason != null || state.value.pausedByUser)
        if (!state.value.isConnected) return
        when (reason) {
            null -> send(MainEffect.Message(R.string.auto_pause_off))
            AutoPause.FaceDown -> send(MainEffect.Message(R.string.auto_pause_face_down))
            AutoPause.FaceUp -> send(MainEffect.Message(R.string.auto_pause_face_up))
            AutoPause.NoSensor -> send(MainEffect.Message(R.string.auto_pause_no_sensor))
        }
        if (reason != null) releasePedals()
    }

    // The current position becomes the center
    fun calibrate() {
        settings.calibrationOffset = -steering.lastAngle
        send(MainEffect.Message(R.string.calibration_completed))
    }

    fun resetCalibration() {
        settings.calibrationOffset = 0f
        send(MainEffect.Message(R.string.calibration_reset))
    }

    private fun applySettings() {
        val game = gameSettings()
        steering.curve = SteeringCurve(game.steeringDeadZone, game.steeringMaxAngle, game.steeringExponent)
        steering.calibrationOffset = settings.calibrationOffset
        steering.smoothness = game.steeringSmoothness
        vibration.value = VibrationSettings(
            enabled = settings.forceFeedback,
            intensity = settings.vibrationStrength / PERCENT,
            road = settings.roadVibration,
            clicks = settings.dashboardClicks,
        )
        pneumaticHorn = settings.pneumaticHorn
        autoPauseEnabled = settings.autoPause
        _state.update {
            it.copy(
                showDashboard = settings.showDashboard,
                actionLayout = game.actionLayout,
                imperialUnits = imperialUnits(),
            )
        }
        analogPedalsMode = settings.pedalMode == PedalMode.Analog
        brakePedal.configure(analogPedalsMode, lockAllowed = false)
        gasPedal.configure(analogPedalsMode, lockAllowed = settings.throttleLock)
        sendPedalsState()
        showPedals()
    }

    private fun send(effect: MainEffect) {
        _effects.trySend(effect)
    }

    private data class VibrationSettings(
        val enabled: Boolean = false,
        val intensity: Float = 0f,
        val road: Boolean = false,
        val clicks: Boolean = false,
    ) {
        val roadOn: Boolean
            get() = enabled && road
    }

    companion object {
        private const val SENSOR_TIMEOUT_MS = 1000L
        private const val PERCENT = 100f

        // A released analog pedal returns from the floor in this time (from a lighter press sooner)
        private const val SPRING_RETURN_MS = 200L
        private const val SPRING_STEP_MS = 16L

        // A tap on a hold action (e.g. "Activate") holds its key at least this long
        private const val MIN_HOLD_MS = 100L

        // The truck state with the cruise speed comes several times per second
        private const val CRUISE_CHECK_MS = 1500L

        // m/s (30 km/h): the games don't turn the cruise control on below it
        private const val CRUISE_MIN_SPEED = 8.3f

        // Where the screen faces (see TiltReading.screenUp): about 37° and 30° below horizontal
        private const val FACE_DOWN_ENTER = -0.6f
        private const val FACE_DOWN_EXIT = -0.5f

        // Lying screen up: about 14° and 20° from horizontal. A phone held as a wheel is tilted back much less,
        // and when it lies there is no steering angle to read anyway
        private const val FACE_UP_ENTER = 0.97f
        private const val FACE_UP_EXIT = 0.94f

        val Factory = viewModelFactory {
            initializer {
                val container = (checkNotNull(this[APPLICATION_KEY]) as TruckRemoteApp).container
                MainViewModel(
                    controller = container.createControllerRepository(),
                    settings = container.settings,
                    tiltSensor = container.tiltSensor,
                    haptics = container.haptics,
                    wifi = container.wifiStatus,
                    analytics = container.analytics,
                )
            }
        }
    }
}

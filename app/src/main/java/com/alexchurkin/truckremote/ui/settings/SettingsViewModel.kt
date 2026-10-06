package com.alexchurkin.truckremote.ui.settings

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.alexchurkin.truckremote.R
import com.alexchurkin.truckremote.TruckRemoteApp
import com.alexchurkin.truckremote.data.billing.BillingEvent
import com.alexchurkin.truckremote.data.billing.BillingManager
import com.alexchurkin.truckremote.data.controller.HapticEvent
import com.alexchurkin.truckremote.data.controller.ServerLink
import com.alexchurkin.truckremote.data.device.HapticCapability
import com.alexchurkin.truckremote.data.device.Haptics
import com.alexchurkin.truckremote.data.sensor.TiltSensor
import com.alexchurkin.truckremote.data.settings.AppMode
import com.alexchurkin.truckremote.data.settings.AppSettings
import com.alexchurkin.truckremote.data.settings.Game
import com.alexchurkin.truckremote.data.settings.GameSettings
import com.alexchurkin.truckremote.data.settings.PedalMode
import com.alexchurkin.truckremote.data.settings.SpeedUnits
import com.alexchurkin.truckremote.domain.SteeringCurve
import com.alexchurkin.truckremote.domain.SteeringProcessor
import com.alexchurkin.truckremote.util.isValidIpv4
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val appMode: AppMode,
    val serverPort: Int,
    val useSpecifiedServer: Boolean,
    val serverIp: String,
    val forceFeedback: Boolean,
    // Percent (see AppSettings.VIBRATION_STRENGTH_RANGE)
    val vibrationStrength: Int,
    val roadVibration: Boolean,
    val dashboardClicks: Boolean,
    val vibrationCapability: HapticCapability,
    val pneumaticHorn: Boolean,
    val showDashboard: Boolean,
    val autoPause: Boolean,
    val steeringDeadZone: Int,
    val steeringMaxAngle: Int,
    val steeringExponent: Float,
    val steeringSmoothness: Int,
    val separateGameSettings: Boolean,
    // The game whose settings are shown (the steering, the panel and the speed units)
    val editedGame: Game,
    val speedUnits: SpeedUnits,
    val calibrated: Boolean,
    val pedalMode: PedalMode,
    val throttleLock: Boolean,
    val adsRemoved: Boolean,
    // Ads are shown in the country of the device (see DataRegion): the purchase that removes them makes sense
    val adsAvailable: Boolean = true,
)

// A snackbar message
data class SettingsMessage(@param:StringRes val text: Int, val args: List<Any> = emptyList()) {
    // A single message, not a hot path: copying the arguments for the vararg doesn't matter
    @Suppress("SpreadOperator")
    fun format(resources: Resources): String = resources.getString(text, *args.toTypedArray())
}

/**
 * Settings are stored in SharedPreferences, the screen observes them,
 * so the state is always actual (also after rotation or a change from another screen).
 */
class SettingsViewModel(
    private val settings: AppSettings,
    private val billing: BillingManager,
    private val tiltSensor: TiltSensor,
    private val haptics: Haptics,
    private val adsAvailable: Boolean = true,
) : ViewModel() {

    // The game being played is shown first
    private val editedGame = MutableStateFlow(settings.lastGame)

    val state: StateFlow<SettingsUiState> = combine(
        settings.changes().onStart { emit(Unit) },
        billing.adsRemoved,
        editedGame,
    ) { _, adsRemoved, _ -> snapshot(adsRemoved) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), snapshot(billing.adsRemoved.value))

    private val game: GameSettings
        get() = settings.game(editedGame.value)

    private val ownMessages = Channel<SettingsMessage>(Channel.BUFFERED)

    val messages: Flow<SettingsMessage> = merge(
        ownMessages.receiveAsFlow(),
        billing.events.map { SettingsMessage(it.messageRes) },
    )

    private val steering = SteeringProcessor()

    /**
     * The steering the phone sends now, from -1 (full lock left) to 1, with the current settings.
     * The sensor works only while the preview is collected (the settings screen is shown).
     */
    val steeringPreview: Flow<Float> = tiltSensor.readings().map { reading ->
        val game = game
        steering.curve = SteeringCurve(game.steeringDeadZone, game.steeringMaxAngle, game.steeringExponent)
        steering.calibrationOffset = settings.calibrationOffset
        steering.smoothness = game.steeringSmoothness
        steering.process(reading.angle, reading.timeNanos) / SteeringCurve.GRAVITY
    }

    fun setServerPort(port: Int) {
        settings.serverPort = port
    }

    fun setUseSpecifiedServer(value: Boolean) {
        settings.useSpecifiedServer = value
    }

    fun setServerIp(ip: String) {
        settings.specifiedServerIp = ip
    }

    // The QR code of the server window: its address is used from now on
    fun applyScannedServer(text: String?) {
        val link = ServerLink.parse(text)
        if (link == null) {
            ownMessages.trySend(SettingsMessage(R.string.scan_qr_wrong))
            return
        }
        settings.specifiedServerIp = link.ip
        settings.serverPort = link.port
        settings.useSpecifiedServer = true
        ownMessages.trySend(SettingsMessage(R.string.scan_qr_done, listOf(link.ip, link.port)))
    }

    fun onScannerLoading() {
        ownMessages.trySend(SettingsMessage(R.string.scan_qr_loading))
    }

    fun onScannerUnavailable() {
        ownMessages.trySend(SettingsMessage(R.string.scan_qr_unavailable))
    }

    fun setForceFeedback(value: Boolean) {
        settings.forceFeedback = value
    }

    fun setVibrationStrength(percent: Int) {
        settings.vibrationStrength = percent
    }

    fun setRoadVibration(value: Boolean) {
        settings.roadVibration = value
    }

    fun setDashboardClicks(value: Boolean) {
        settings.dashboardClicks = value
    }

    private var vibrationTest: Job? = null

    // A few events of the game at the chosen strength: a gear shift, a bump and a collision
    fun testVibration() {
        vibrationTest?.cancel()
        vibrationTest = viewModelScope.launch {
            val intensity = settings.vibrationStrength / PERCENT
            TEST_EVENTS.forEach { (event, strength) ->
                haptics.play(event, strength, intensity)
                delay(TEST_INTERVAL_MS)
            }
        }
    }

    override fun onCleared() {
        haptics.stop()
    }

    fun setPneumaticHorn(value: Boolean) {
        settings.pneumaticHorn = value
    }

    fun setShowDashboard(value: Boolean) {
        settings.showDashboard = value
    }

    fun setAutoPause(value: Boolean) {
        settings.autoPause = value
    }

    fun setSteeringDeadZone(degrees: Int) {
        game.steeringDeadZone = degrees
    }

    fun setSteeringMaxAngle(degrees: Int) {
        game.steeringMaxAngle = degrees
    }

    fun setSteeringExponent(value: Float) {
        game.steeringExponent = value
    }

    fun setSteeringSmoothness(level: Int) {
        game.steeringSmoothness = level
    }

    fun setSeparateGameSettings(value: Boolean) {
        settings.separateGameSettings = value
    }

    fun selectEditedGame(game: Game) {
        editedGame.value = game
    }

    // The settings of the other game replace the shown ones
    fun copyFromOtherGame() {
        val other = if (editedGame.value == Game.Ets2) Game.Ats else Game.Ets2
        game.copyFrom(settings.game(other))
        ownMessages.trySend(SettingsMessage(R.string.game_settings_copied, listOf(other.title)))
    }

    fun setAppMode(mode: AppMode) {
        settings.appMode = mode
    }

    fun setSpeedUnits(units: SpeedUnits) {
        game.speedUnits = units
    }

    // The current tilt becomes the straight wheel
    fun calibrate() {
        settings.calibrationOffset = -steering.lastAngle
        ownMessages.trySend(SettingsMessage(R.string.calibration_completed))
    }

    fun resetCalibration() {
        settings.calibrationOffset = 0f
        ownMessages.trySend(SettingsMessage(R.string.calibration_reset))
    }

    fun setPedalMode(mode: PedalMode) {
        settings.pedalMode = mode
    }

    // The result comes as a message
    fun restorePurchase() = billing.restorePurchase()

    fun setThrottleLock(value: Boolean) {
        settings.throttleLock = value
    }

    private fun snapshot(adsRemoved: Boolean) = SettingsUiState(
        appMode = settings.appMode ?: AppMode.Controller,
        serverPort = settings.serverPort,
        useSpecifiedServer = settings.useSpecifiedServer,
        serverIp = settings.specifiedServerIp,
        forceFeedback = settings.forceFeedback,
        vibrationStrength = settings.vibrationStrength,
        roadVibration = settings.roadVibration,
        dashboardClicks = settings.dashboardClicks,
        vibrationCapability = haptics.capability,
        pneumaticHorn = settings.pneumaticHorn,
        showDashboard = settings.showDashboard,
        autoPause = settings.autoPause,
        steeringDeadZone = game.steeringDeadZone,
        steeringMaxAngle = game.steeringMaxAngle,
        steeringExponent = game.steeringExponent,
        steeringSmoothness = game.steeringSmoothness,
        separateGameSettings = settings.separateGameSettings,
        editedGame = editedGame.value,
        speedUnits = game.speedUnits,
        calibrated = settings.calibrationOffset != 0f,
        pedalMode = settings.pedalMode,
        throttleLock = settings.throttleLock,
        adsRemoved = adsRemoved,
        adsAvailable = adsAvailable,
    )

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val PERCENT = 100f
        private const val TEST_INTERVAL_MS = 700L
        private val TEST_EVENTS = listOf(
            HapticEvent.GearShift to 1f,
            HapticEvent.Bump to 0.7f,
            HapticEvent.Collision to 1f,
        )

        fun parsePort(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in AppSettings.PORT_RANGE }

        fun isValidIp(text: String): Boolean = isValidIpv4(text.trim())

        val Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as TruckRemoteApp
                SettingsViewModel(
                    app.container.settings,
                    app.container.billing,
                    app.container.tiltSensor,
                    app.container.haptics,
                    app.container.region.adsAllowed,
                )
            }
        }
    }
}

// Game names are the same in every language
val Game.title: String
    get() = when (this) {
        Game.Ets2 -> "ETS2"
        Game.Ats -> "ATS"
    }

@get:StringRes
private val BillingEvent.messageRes: Int
    get() = when (this) {
        BillingEvent.Restored -> R.string.purchase_restored
        BillingEvent.Returned -> R.string.purchase_returned
        BillingEvent.NotFound -> R.string.purchase_not_found
        BillingEvent.Failed -> R.string.purchase_check_failed
    }

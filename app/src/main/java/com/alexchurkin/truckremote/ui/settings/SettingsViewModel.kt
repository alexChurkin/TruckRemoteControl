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
import com.alexchurkin.truckremote.data.controller.ServerLink
import com.alexchurkin.truckremote.data.sensor.TiltSensor
import com.alexchurkin.truckremote.data.settings.AppSettings
import com.alexchurkin.truckremote.data.settings.PedalMode
import com.alexchurkin.truckremote.domain.SteeringCurve
import com.alexchurkin.truckremote.domain.SteeringProcessor
import com.alexchurkin.truckremote.util.isValidIpv4
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn

data class SettingsUiState(
    val serverPort: Int,
    val useSpecifiedServer: Boolean,
    val serverIp: String,
    val forceFeedback: Boolean,
    val pneumaticHorn: Boolean,
    val showDashboard: Boolean,
    val steeringDeadZone: Int,
    val steeringMaxAngle: Int,
    val steeringExponent: Float,
    val steeringSmoothness: Int,
    val calibrated: Boolean,
    val pedalMode: PedalMode,
    val throttleLock: Boolean,
    val adsRemoved: Boolean,
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
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = combine(
        settings.changes().onStart { emit(Unit) },
        billing.adsRemoved,
    ) { _, adsRemoved -> snapshot(adsRemoved) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), snapshot(billing.adsRemoved.value))

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
        steering.curve = SteeringCurve(settings.steeringDeadZone, settings.steeringMaxAngle, settings.steeringExponent)
        steering.calibrationOffset = settings.calibrationOffset
        steering.smoothness = settings.steeringSmoothness
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

    fun setPneumaticHorn(value: Boolean) {
        settings.pneumaticHorn = value
    }

    fun setShowDashboard(value: Boolean) {
        settings.showDashboard = value
    }

    fun setSteeringDeadZone(degrees: Int) {
        settings.steeringDeadZone = degrees
    }

    fun setSteeringMaxAngle(degrees: Int) {
        settings.steeringMaxAngle = degrees
    }

    fun setSteeringExponent(value: Float) {
        settings.steeringExponent = value
    }

    fun setSteeringSmoothness(level: Int) {
        settings.steeringSmoothness = level
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
        serverPort = settings.serverPort,
        useSpecifiedServer = settings.useSpecifiedServer,
        serverIp = settings.specifiedServerIp,
        forceFeedback = settings.forceFeedback,
        pneumaticHorn = settings.pneumaticHorn,
        showDashboard = settings.showDashboard,
        steeringDeadZone = settings.steeringDeadZone,
        steeringMaxAngle = settings.steeringMaxAngle,
        steeringExponent = settings.steeringExponent,
        steeringSmoothness = settings.steeringSmoothness,
        calibrated = settings.calibrationOffset != 0f,
        pedalMode = settings.pedalMode,
        throttleLock = settings.throttleLock,
        adsRemoved = adsRemoved,
    )

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun parsePort(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in AppSettings.PORT_RANGE }

        fun isValidIp(text: String): Boolean = isValidIpv4(text.trim())

        val Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as TruckRemoteApp
                SettingsViewModel(app.container.settings, app.container.billing, app.container.tiltSensor)
            }
        }
    }
}

@get:StringRes
private val BillingEvent.messageRes: Int
    get() = when (this) {
        BillingEvent.Restored -> R.string.purchase_restored
        BillingEvent.Returned -> R.string.purchase_returned
        BillingEvent.NotFound -> R.string.purchase_not_found
        BillingEvent.Failed -> R.string.purchase_check_failed
    }

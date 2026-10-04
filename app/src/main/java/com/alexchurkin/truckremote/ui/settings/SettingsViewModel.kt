package com.alexchurkin.truckremote.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.alexchurkin.truckremote.TruckRemoteApp
import com.alexchurkin.truckremote.data.billing.BillingEvent
import com.alexchurkin.truckremote.data.billing.BillingManager
import com.alexchurkin.truckremote.data.settings.AppSettings
import com.alexchurkin.truckremote.data.settings.PedalMode
import com.alexchurkin.truckremote.util.isValidIpv4
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

data class SettingsUiState(
    val serverPort: Int,
    val useSpecifiedServer: Boolean,
    val serverIp: String,
    val forceFeedback: Boolean,
    val pneumaticHorn: Boolean,
    val steeringDeadZone: Int,
    val steeringMaxAngle: Int,
    val steeringExponent: Float,
    val pedalMode: PedalMode,
    val throttleLock: Boolean,
    val adsRemoved: Boolean,
)

/**
 * Settings are stored in SharedPreferences, the screen observes them,
 * so the state is always actual (also after rotation or a change from another screen).
 */
class SettingsViewModel(private val settings: AppSettings, private val billing: BillingManager) : ViewModel() {

    val state: StateFlow<SettingsUiState> = combine(
        settings.changes().onStart { emit(Unit) },
        billing.adsRemoved,
    ) { _, adsRemoved -> snapshot(adsRemoved) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), snapshot(billing.adsRemoved.value))

    val billingEvents: Flow<BillingEvent> = billing.events

    fun setServerPort(port: Int) {
        settings.serverPort = port
    }

    fun setUseSpecifiedServer(value: Boolean) {
        settings.useSpecifiedServer = value
    }

    fun setServerIp(ip: String) {
        settings.specifiedServerIp = ip
    }

    fun setForceFeedback(value: Boolean) {
        settings.forceFeedback = value
    }

    fun setPneumaticHorn(value: Boolean) {
        settings.pneumaticHorn = value
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

    fun setPedalMode(mode: PedalMode) {
        settings.pedalMode = mode
    }

    // The result comes as a billing event
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
        steeringDeadZone = settings.steeringDeadZone,
        steeringMaxAngle = settings.steeringMaxAngle,
        steeringExponent = settings.steeringExponent,
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
                SettingsViewModel(app.container.settings, app.container.billing)
            }
        }
    }
}

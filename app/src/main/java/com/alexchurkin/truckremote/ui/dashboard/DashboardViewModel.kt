package com.alexchurkin.truckremote.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.alexchurkin.truckremote.TruckRemoteApp
import com.alexchurkin.truckremote.data.controller.Job
import com.alexchurkin.truckremote.data.controller.ServerState
import com.alexchurkin.truckremote.data.settings.AppSettings
import com.alexchurkin.truckremote.data.settings.Game
import com.alexchurkin.truckremote.data.viewer.ViewerClient
import com.alexchurkin.truckremote.data.viewer.ViewerState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

data class DashboardUiState(
    // null while the server is searched for
    val serverAddress: String? = null,
    // null until the server sends its first state
    val truck: ServerState? = null,
    val job: Job? = null,
    val imperialUnits: Boolean = false,
    val speedingWarning: Boolean = true,
)

/**
 * The dashboard mode: the truck state of the server for a tablet or a second phone (a viewer, it controls nothing).
 * The viewer works only while the screen is shown.
 */
class DashboardViewModel(private val settings: AppSettings, private val viewer: ViewerClient) : ViewModel() {

    val state: StateFlow<DashboardUiState> = combine(
        viewer.state,
        settings.changes().onStart { emit(Unit) },
    ) { viewerState, _ -> snapshot(viewerState) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DashboardUiState())

    fun setForeground(shown: Boolean) = if (shown) viewer.start() else viewer.stop()

    override fun onCleared() = viewer.stop()

    private fun snapshot(viewerState: ViewerState): DashboardUiState {
        val game = if (viewerState.truck?.dashboard?.isAts == true) Game.Ats else Game.Ets2
        return DashboardUiState(
            serverAddress = viewerState.serverAddress,
            truck = viewerState.truck,
            job = viewerState.job,
            imperialUnits = settings.game(
                game,
            ).speedUnits.isImperial(game, viewerState.truck?.dashboard?.gameImperialUnits),
            speedingWarning = settings.speedingWarning,
        )
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val settings = (checkNotNull(this[APPLICATION_KEY]) as TruckRemoteApp).container.settings
                // The server the controller found or was given is asked first, then it is searched for
                val known = listOfNotNull(
                    settings.specifiedServerIp.takeIf { settings.useSpecifiedServer && it.isNotBlank() },
                    settings.lastServerIp,
                )
                DashboardViewModel(settings, ViewerClient(settings.serverPort, known))
            }
        }
    }
}

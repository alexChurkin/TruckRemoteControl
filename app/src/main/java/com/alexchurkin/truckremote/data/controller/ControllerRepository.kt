package com.alexchurkin.truckremote.data.controller

import com.alexchurkin.truckremote.data.device.LowLatencyWifiLock
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Connection to Truck Remote Server: the controller state goes to the server,
 * the truck state comes back. Flows may be updated from network threads.
 */
interface ControllerRepository {
    val connectionState: StateFlow<ConnectionState>

    // Truck state without force feedback; null when it is unknown (no connection)
    val truckState: StateFlow<ServerState?>

    // Force feedback effects of the game, their durations in ms
    val forceFeedback: Flow<Long>

    val linkQuality: StateFlow<LinkQuality?>

    val serverAddress: String?

    // ip = null searches for the server, knownIp is asked directly during the search
    fun connect(ip: String?, port: Int, knownIp: String?)

    fun disconnect()

    // The app is on screen; in background the server releases the controls
    fun setForeground(foreground: Boolean)

    fun setPausedByUser(paused: Boolean)

    fun updateState(transform: (ControllerState) -> ControllerState)

    fun clickAction(action: ControllerAction)

    fun setActionHeld(action: ControllerAction, held: Boolean)
}

class UdpControllerRepository(private val wifiLock: LowLatencyWifiLock) :
    ControllerRepository,
    TrackingClient.Listener {

    private val client = TrackingClient(this)

    // The last job received, joined to the truck state
    @Volatile
    private var job: Job? = null

    private val _connectionState = MutableStateFlow(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _truckState = MutableStateFlow<ServerState?>(null)
    override val truckState: StateFlow<ServerState?> = _truckState.asStateFlow()

    private val _forceFeedback = MutableSharedFlow<Long>(
        extraBufferCapacity = FFB_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val forceFeedback: Flow<Long> = _forceFeedback.asSharedFlow()

    private val _linkQuality = MutableStateFlow<LinkQuality?>(null)
    override val linkQuality: StateFlow<LinkQuality?> = _linkQuality.asStateFlow()

    override val serverAddress: String?
        get() = client.serverAddress

    override fun connect(ip: String?, port: Int, knownIp: String?) = client.start(ip, port, knownIp)

    override fun disconnect() = client.stop()

    override fun setForeground(foreground: Boolean) {
        if (foreground) {
            client.resume()
            wifiLock.acquire()
        } else {
            client.pause()
            wifiLock.release()
        }
    }

    override fun setPausedByUser(paused: Boolean) = if (paused) client.pauseByUser() else client.resumeByUser()

    override fun updateState(transform: (ControllerState) -> ControllerState) = client.updateState(transform)

    override fun clickAction(action: ControllerAction) = client.clickAction(action)

    override fun setActionHeld(action: ControllerAction, held: Boolean) = client.setActionHeld(action, held)

    override fun onConnectionStateChanged(state: ConnectionState) {
        _connectionState.value = state
        if (!state.isConnected) {
            job = null
            _truckState.value = null
            _linkQuality.value = null
        }
    }

    // The server sends its state 50 times per second, the state flow keeps only changes
    override fun onServerState(state: ServerState) {
        if (state.ffbDurationMs > 0) _forceFeedback.tryEmit(state.ffbDurationMs)
        _truckState.value = state.copy(ffbDurationMs = 0, sequence = null, job = job)
    }

    override fun onJob(job: Job?) {
        this.job = job
    }

    override fun onLinkQuality(quality: LinkQuality) {
        _linkQuality.value = quality
    }

    private companion object {
        const val FFB_BUFFER = 4
    }
}

// Connected or resuming the session: the controls work
val ConnectionState.isConnected: Boolean
    get() = this == ConnectionState.Connected || this == ConnectionState.Resuming

package com.alexchurkin.truckremote.data.viewer

import android.os.SystemClock
import com.alexchurkin.truckremote.data.controller.BinaryProtocol
import com.alexchurkin.truckremote.data.controller.ControllerProtocol
import com.alexchurkin.truckremote.data.controller.Job
import com.alexchurkin.truckremote.data.controller.ServerState
import com.alexchurkin.truckremote.data.controller.broadcastAddresses
import com.alexchurkin.truckremote.util.logD
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// What the dashboard shows: the server found and its truck state, null while it is being searched for
data class ViewerState(val serverAddress: String? = null, val truck: ServerState? = null, val job: Job? = null)

/**
 * A viewer of Truck Remote Server (server revision 6+): the dashboard of a tablet or another phone. It controls
 * nothing, so it works beside the controller. The hello is sent once a second (it keeps the viewer known to the
 * server): to the found server, or while searching to the known addresses and the broadcasts. The server sends
 * its state 20 times per second; after [LOST_AFTER_MS] without it the server is searched for again.
 */
class ViewerClient(private val port: Int, private val knownAddresses: List<String>) {

    private val _state = MutableStateFlow(ViewerState())
    val state: StateFlow<ViewerState> = _state.asStateFlow()

    @Volatile
    private var running = false

    @Volatile
    private var server: InetAddress? = null

    @Volatile
    private var lastStateTime = 0L
    private var socket: DatagramSocket? = null
    private var receiver: Thread? = null
    private var sender: Thread? = null

    fun start() {
        if (running) return
        running = true
        val socket = DatagramSocket().apply {
            broadcast = true
            soTimeout = RECEIVE_TIMEOUT_MS
        }
        this.socket = socket
        receiver = thread(name = "ViewerClient-receive", isDaemon = true) { receive(socket) }
        sender = thread(name = "ViewerClient-hello", isDaemon = true) { sendHellos(socket) }
    }

    // The goodbye lets the server forget the viewer at once; the network isn't used on the calling (main) thread
    fun stop() {
        if (!running) return
        running = false
        val socket = socket ?: return
        val address = server
        sender?.interrupt()
        this.socket = null
        server = null
        _state.value = ViewerState()
        thread(name = "ViewerClient-goodbye", isDaemon = true) {
            address?.let { send(socket, BinaryProtocol.GOODBYE, it) }
            socket.close()
        }
    }

    private fun sendHellos(socket: DatagramSocket) {
        val hello = HELLO.toByteArray()
        while (running) {
            val found = server
            if (found != null && SystemClock.elapsedRealtime() - lastStateTime > LOST_AFTER_MS) {
                logD("Viewer lost the server $found")
                server = null
                _state.value = ViewerState()
            }
            val targets = server?.let { listOf(it) } ?: searchTargets()
            targets.forEach { send(socket, hello, it) }
            try {
                Thread.sleep(HELLO_INTERVAL_MS)
            } catch (_: InterruptedException) {
                return
            }
        }
    }

    private fun searchTargets(): List<InetAddress> =
        (knownAddresses.mapNotNull { runCatching { InetAddress.getByName(it) }.getOrNull() } + broadcastAddresses())
            .distinct()

    private fun receive(socket: DatagramSocket) {
        val buffer = ByteArray(BUFFER_SIZE)
        while (running) {
            val packet = DatagramPacket(buffer, buffer.size)
            try {
                socket.receive(packet)
            } catch (_: SocketTimeoutException) {
                continue
            } catch (e: IOException) {
                if (running) logD("Viewer receive error: $e")
                return
            }
            onMessage(packet)
        }
    }

    private fun onMessage(packet: DatagramPacket) {
        val found = server
        if (found == null) {
            onSearchAnswer(packet)
        } else if (packet.address == found) {
            onServerMessage(packet)
        }
    }

    // While searching the first server that answers is taken, then only its messages are read
    private fun onSearchAnswer(packet: DatagramPacket) {
        if (String(packet.data, 0, packet.length, Charsets.UTF_8) != ControllerProtocol.BINARY_HELLO_ANSWER) return
        server = packet.address
        lastStateTime = SystemClock.elapsedRealtime()
        _state.value = ViewerState(serverAddress = packet.address.hostAddress)
    }

    private fun onServerMessage(packet: DatagramPacket) {
        val data = packet.data
        val length = packet.length
        when {
            !BinaryProtocol.isBinary(data, length) -> Unit

            BinaryProtocol.isJob(data, length) ->
                _state.value =
                    _state.value.copy(job = BinaryProtocol.decodeJob(data, length))

            else -> BinaryProtocol.decodeServerState(data, length)?.let { truck ->
                lastStateTime = SystemClock.elapsedRealtime()
                _state.value = _state.value.copy(truck = truck.copy(sequence = null))
            }
        }
    }

    private fun send(socket: DatagramSocket, bytes: ByteArray, address: InetAddress) {
        try {
            socket.send(DatagramPacket(bytes, bytes.size, InetSocketAddress(address, port)))
        } catch (e: IOException) {
            logD("Viewer can't send to $address: $e")
        }
    }

    private companion object {
        const val HELLO = "TruckRemoteViewer" + BinaryProtocol.VERSION
        const val HELLO_INTERVAL_MS = 1000L
        const val LOST_AFTER_MS = 3000L
        const val RECEIVE_TIMEOUT_MS = 500
        const val BUFFER_SIZE = 1024
    }
}

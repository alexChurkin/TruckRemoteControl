package com.alexchurkin.truckremote.net

import com.alexchurkin.truckremote.util.logD
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import kotlin.concurrent.thread

/**
 * UDP client of Truck Remote Server.
 * Sends the controller state and receives the truck state in lockstep with the server.
 * Listener methods are called on the network thread.
 */
class TrackingClient(private val listener: Listener) {

    interface Listener {
        fun onConnectionChanged(connected: Boolean)

        fun onServerState(state: ServerState)
    }

    @Volatile
    var serverAddress: String? = null
        private set

    @Volatile
    var isPaused = false
        private set

    @Volatile
    var isPausedByUser = false
        private set

    @Volatile
    var lastServerState: ServerState? = null
        private set

    val isAnalogPedalsAvailable: Boolean
        get() = lastServerState?.analogPedalsAvailable == true

    private val stateLock = Any()
    private var state = ControllerState()

    @Volatile
    private var session: Session? = null

    fun updateState(transform: (ControllerState) -> ControllerState) {
        synchronized(stateLock) { state = transform(state) }
    }

    fun clickAction(action: ControllerAction) = updateState { current ->
        val counters = current.actionCounters.toMutableList()
        counters[action.ordinal]++
        current.copy(actionCounters = counters)
    }

    // ip = null means searching for the server by broadcast
    fun start(ip: String?, port: Int) {
        stop()
        isPaused = false
        isPausedByUser = false
        serverAddress = ip
        session = Session(ip, port).also { it.start() }
    }

    fun stop() {
        session?.stop()
        session = null
    }

    fun pause() {
        isPaused = true
    }

    fun resume() {
        isPaused = false
    }

    fun pauseByUser() {
        isPausedByUser = true
    }

    fun resumeByUser() {
        isPausedByUser = false
    }

    private inner class Session(private val ip: String?, private val port: Int) {
        @Volatile
        private var running = true
        private val socket = DatagramSocket().apply { soTimeout = RECEIVE_TIMEOUT_MS }

        fun start() {
            thread(name = "TrackingClient", isDaemon = true) { run() }
        }

        // Says goodbye (so the server releases controls immediately) and closes the socket
        fun stop() {
            running = false
            thread(name = "TrackingClient-stop", isDaemon = true) {
                runCatching { if (socket.isConnected) send(ControllerProtocol.GOODBYE) }
                socket.close()
            }
        }

        // A replaced session must not report its state over the new one
        private val isReportable get() = session === this || session == null

        private fun run() {
            lastServerState = null
            try {
                if (connect()) {
                    if (isReportable) listener.onConnectionChanged(true)
                    exchangeMessages()
                }
            } catch (e: IOException) {
                logD("Connection error: $e")
            }
            socket.close()
            if (isReportable) listener.onConnectionChanged(false)
        }

        // Hello is repeated a few times: UDP packets may be lost
        private fun connect(): Boolean {
            val addresses = if (ip != null) listOf(InetAddress.getByName(ip)) else broadcastAddresses()
            socket.broadcast = ip == null
            val hello = ControllerProtocol.HELLO.toByteArray()
            val answer = DatagramPacket(ByteArray(BUFFER_SIZE), BUFFER_SIZE)

            repeat(HELLO_ATTEMPTS) {
                if (!running) return false
                addresses.forEach { address ->
                    // Some interfaces can't send broadcasts, the others should still be tried
                    try {
                        socket.send(DatagramPacket(hello, hello.size, address, port))
                    } catch (e: IOException) {
                        logD("Can't send hello to $address: $e")
                    }
                }
                try {
                    socket.receive(answer)
                    socket.broadcast = false
                    socket.connect(answer.socketAddress)
                    serverAddress = answer.address.hostAddress
                    return true
                } catch (_: SocketTimeoutException) {
                    logD("No answer to hello")
                }
            }
            return false
        }

        private fun exchangeMessages() {
            var timeouts = 0
            val packet = DatagramPacket(ByteArray(BUFFER_SIZE), BUFFER_SIZE)

            while (running) {
                if (isPaused || isPausedByUser) {
                    send(ControllerProtocol.PAUSED)
                    Thread.sleep(PAUSED_INTERVAL_MS)
                    continue
                }

                send(ControllerProtocol.encode(synchronized(stateLock) { state }))
                try {
                    // Length is reduced by every received packet
                    packet.length = BUFFER_SIZE
                    socket.receive(packet)
                    timeouts = 0
                    val message = String(packet.data, 0, packet.length)
                    ControllerProtocol.decodeServerMessage(message)?.let {
                        lastServerState = it
                        listener.onServerState(it)
                    }
                } catch (_: SocketTimeoutException) {
                    if (++timeouts > MAX_TIMEOUTS) return
                }
            }
        }

        /*
         * 255.255.255.255 goes only through the default interface, which may be wrong
         * (e.g. the phone shares a hotspot or has mobile data), so subnet broadcasts are added.
         */
        private fun broadcastAddresses(): List<InetAddress> {
            val subnetBroadcasts = try {
                NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                    .filter { it.isUp && !it.isLoopback }
                    .flatMap { it.interfaceAddresses }
                    .filter { it.address is Inet4Address }
                    .mapNotNull { it.broadcast }
            } catch (e: IOException) {
                logD("Can't get network interfaces: $e")
                emptyList()
            }
            return (listOf(InetAddress.getByName(BROADCAST_ADDRESS)) + subnetBroadcasts).distinct()
        }

        private fun send(text: String) {
            val bytes = text.toByteArray()
            socket.send(DatagramPacket(bytes, bytes.size))
        }
    }

    private companion object {
        const val BROADCAST_ADDRESS = "255.255.255.255"
        const val RECEIVE_TIMEOUT_MS = 600
        const val HELLO_ATTEMPTS = 3
        const val MAX_TIMEOUTS = 2
        const val PAUSED_INTERVAL_MS = 500L
        const val BUFFER_SIZE = 256
    }
}

package com.alexchurkin.truckremote.data.controller

import android.os.SystemClock
import com.alexchurkin.truckremote.util.logD
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import kotlin.concurrent.thread

enum class ConnectionState {
    // First search of the server
    Searching,

    // The server didn't answer, the search is repeated in background
    NotFound,

    Connected,

    // Server messages stopped coming for a moment, the session is being resumed
    Resuming,

    // The connection was lost, reconnecting in background
    Lost,

    // Stopped by the user
    Disconnected,
}

/**
 * UDP client of Truck Remote Server.
 * The controller state is sent at a constant rate on its own thread, server messages are received on another one,
 * so a delayed or lost server message doesn't delay the controls.
 * After a connection loss the client reconnects by itself until [stop] is called.
 * Listener methods are called on the network threads.
 */
class TrackingClient(private val listener: Listener) {

    interface Listener {
        fun onConnectionStateChanged(state: ConnectionState)

        fun onServerState(state: ServerState)

        // The job is sent once a second, null - no job
        fun onJob(job: Job?)

        // What the player has no key for in the game, sent once a second by the server revision 8+
        fun onUnboundKeys(keys: UnboundKeys)

        // About once per second while connected
        fun onLinkQuality(quality: LinkQuality)
    }

    @Volatile
    var serverAddress: String? = null
        private set

    @Volatile
    var connectionState = ConnectionState.Disconnected
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

    @Volatile
    var lastLinkQuality: LinkQuality? = null
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

    fun clickAction(action: ControllerAction) = updateState { it.withClick(action) }

    fun setActionHeld(action: ControllerAction, held: Boolean) = updateState { it.withHeld(action, held) }

    /**
     * [ip] = null means searching for the server by broadcast;
     * [knownIp] (e.g. the server found last time) is asked directly during the search.
     */
    fun start(ip: String?, port: Int, knownIp: String? = null) {
        stop()
        isPaused = false
        isPausedByUser = false
        serverAddress = ip
        session = Session(ip, knownIp, port).also { it.start() }
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

    private inner class Session(private val ip: String?, private val knownIp: String?, private val port: Int) {
        @Volatile
        private var running = true

        // Android ties a socket to the network it was first used on: after that network is gone
        // (e.g. Wi-Fi reconnected) sending fails with ENETUNREACH forever, so the socket is recreated then
        @Volatile
        private var socket = DatagramSocket().apply { soTimeout = HANDSHAKE_TIMEOUT_MS }

        @Volatile
        private var sendFailed = false

        // The server speaks the binary protocol (it answered the hello with its version)
        @Volatile
        private var binary = false
        private var mainThread: Thread? = null

        private val meter = LinkQualityMeter()
        private var sequence = 0L

        @Volatile
        private var lastMessageTime = 0L

        @Volatile
        private var resuming = false

        @Volatile
        private var exchanging = false

        // The previous message was "paused" (the sender thread only)
        private var wasPaused = false

        fun start() {
            mainThread = thread(name = "TrackingClient", isDaemon = true) { run() }
        }

        // Says goodbye (so the server releases controls immediately) and closes the socket
        fun stop() {
            running = false
            mainThread?.interrupt()
            thread(name = "TrackingClient-stop", isDaemon = true) {
                runCatching {
                    if (socket.isConnected) {
                        if (binary) send(BinaryProtocol.GOODBYE) else send(ControllerProtocol.GOODBYE)
                    }
                }
                socket.close()
            }
        }

        // A replaced session must not report its state over the new one
        private val isReportable get() = session === this || session == null

        private fun report(newState: ConnectionState) {
            if (!isReportable || connectionState == newState) return
            logD("Connection: $connectionState -> $newState")
            connectionState = newState
            listener.onConnectionStateChanged(newState)
        }

        private var connectedOnce = false
        private var failedAttempts = 0

        private fun run() {
            report(ConnectionState.Searching)
            try {
                while (running) connectOrRecover()
            } catch (_: InterruptedException) {
                // Stopped
            }
            socket.close()
            lastServerState = null
            report(ConnectionState.Disconnected)
        }

        // A network error (e.g. the network is gone or the server port is closed) doesn't end the session:
        // it goes on with a new socket
        private fun connectOrRecover() {
            try {
                connectAndExchange()
            } catch (e: IOException) {
                if (!running) return
                logD("Connection error: $e")
                sendFailed = true
                report(if (connectedOnce) ConnectionState.Lost else ConnectionState.NotFound)
                Thread.sleep(reconnectDelayMs(failedAttempts++))
            }
        }

        // Returns when the connection is lost or the server isn't found (after a delay)
        private fun connectAndExchange() {
            if (handshake()) {
                connectedOnce = true
                failedAttempts = 0
                report(ConnectionState.Connected)
                exchangeMessages()
                // The socket may be tied to a network that is gone (sending doesn't always fail then):
                // a new one is used
                sendFailed = true
                if (running) report(ConnectionState.Lost)
            } else if (running) {
                // The specified server didn't answer: the search starts at once, "not found" is told after it
                val searched = isSearching
                if (!connectedOnce && searched) report(ConnectionState.NotFound)
                val attempt = failedAttempts++
                if (searched) Thread.sleep(reconnectDelayMs(attempt))
            }
        }

        // Returns false if the session is stopped
        private fun recreateSocketIfSendingFailed(): Boolean {
            if (!sendFailed) return true
            sendFailed = false
            logD("Sending failed, the socket is recreated")
            socket.close()
            if (!running) return false
            socket = DatagramSocket()
            return true
        }

        // Hello is repeated a few times: UDP packets may be lost
        private fun handshake(): Boolean {
            if (!recreateSocketIfSendingFailed()) return false
            if (socket.isConnected) socket.disconnect()
            val addresses = targetAddresses()
            socket.broadcast = isSearching
            socket.soTimeout = HANDSHAKE_TIMEOUT_MS
            val hello = ControllerProtocol.HELLO.toByteArray()
            val answer = DatagramPacket(ByteArray(BUFFER_SIZE), BUFFER_SIZE)

            repeat(HELLO_ATTEMPTS) {
                if (!running) return false
                sendHello(hello, addresses)
                if (receiveHelloAnswer(answer)) return true
            }
            return false
        }

        private fun sendHello(hello: ByteArray, addresses: List<InetAddress>) {
            addresses.forEach { address ->
                // Some interfaces can't send broadcasts, the others should still be tried
                try {
                    socket.send(DatagramPacket(hello, hello.size, address, port))
                } catch (e: IOException) {
                    logD("Can't send hello to $address: $e")
                    sendFailed = true
                }
            }
        }

        // The server that answered first is used. Other packets (the truck state of the session being resumed)
        // aren't answers: taking one would choose the wrong protocol
        private fun receiveHelloAnswer(answer: DatagramPacket): Boolean {
            val deadline = SystemClock.elapsedRealtime() + HANDSHAKE_TIMEOUT_MS
            try {
                while (SystemClock.elapsedRealtime() < deadline) {
                    answer.length = BUFFER_SIZE
                    socket.receive(answer)
                    val text = String(answer.data, 0, answer.length)
                    if (BinaryProtocol.isBinary(answer.data, answer.length) ||
                        !text.startsWith(ControllerProtocol.HELLO_ANSWER)
                    ) {
                        continue
                    }
                    socket.broadcast = false
                    socket.connect(answer.socketAddress)
                    serverAddress = answer.address.hostAddress
                    binary = text == ControllerProtocol.BINARY_HELLO_ANSWER
                    return true
                }
            } catch (_: SocketTimeoutException) {
                // No answer
            }
            logD("No answer to hello")
            return false
        }

        // The specified server is only asked directly at first. If it doesn't answer, the search is added:
        // the PC may have got another address from the router, and the saved one would never work again
        private val isSearching: Boolean
            get() = ip == null || failedAttempts > 0

        /*
         * Searching uses broadcasts and the known servers:
         * 255.255.255.255 goes only through the default interface, which may be wrong
         * (e.g. the phone shares a hotspot or has mobile data), so subnet broadcasts are added.
         * After a loss the found server is asked first (it may have got another address, so the search goes on too).
         */
        private fun targetAddresses(): List<InetAddress> {
            if (!isSearching) return listOf(InetAddress.getByName(ip))
            val direct = listOfNotNull(ip, serverAddress, knownIp).distinct().mapNotNull {
                runCatching { InetAddress.getByName(it) }.getOrNull()
            }
            return (direct + broadcastAddresses()).distinct()
        }

        // Returns when the connection is lost
        private fun exchangeMessages() {
            meter.reset()
            resuming = false
            lastMessageTime = SystemClock.elapsedRealtime()
            socket.soTimeout = RECEIVE_TIMEOUT_MS

            exchanging = true
            val sender = thread(name = "TrackingClient-send", isDaemon = true) {
                // Fixed rate: the interval doesn't grow by the time of sending
                var nextSendNs = System.nanoTime()
                while (running && exchanging) {
                    val intervalMs = sendNext()
                    nextSendNs += intervalMs * NANOS_IN_MS
                    val waitMs = (nextSendNs - System.nanoTime()) / NANOS_IN_MS
                    // After a long delay messages aren't sent in a burst to catch up
                    if (waitMs < -intervalMs) nextSendNs = System.nanoTime()
                    if (waitMs > 0) {
                        try {
                            Thread.sleep(waitMs)
                        } catch (_: InterruptedException) {
                            break
                        }
                    }
                }
            }
            try {
                receiveMessages()
            } finally {
                exchanging = false
                sender.interrupt()
            }
        }

        // Returns the interval before the next message
        private fun sendNext(): Long {
            val paused = isPaused || isPausedByUser
            try {
                if (paused) {
                    if (binary) send(BinaryProtocol.PAUSED) else send(ControllerProtocol.PAUSED)
                } else {
                    // In background the system may block the network, so the server has dropped the paused session:
                    // hello restores it at once (it is harmless for a session that is alive)
                    if (wasPaused) send(ControllerProtocol.HELLO)
                    val current = synchronized(stateLock) { state }
                    sequence++
                    if (binary) {
                        send(BinaryProtocol.encodeState(current, sequence))
                    } else {
                        send(ControllerProtocol.encode(current, sequence))
                    }
                    // Resuming: the server may have dropped the session, hello restores it
                    if (resuming && sequence % HELLO_EVERY_N_MESSAGES == 0L) send(ControllerProtocol.HELLO)
                }
            } catch (e: IOException) {
                // E.g. the network is unavailable for a moment: the receiver decides when the connection is lost
                logD("Send error: $e")
                sendFailed = true
            }
            wasPaused = paused
            return if (paused) PAUSED_INTERVAL_MS else SEND_INTERVAL_MS
        }

        // A timeout isn't an error: silence is checked by the caller
        private fun receiveMessage(packet: DatagramPacket) {
            try {
                // Length is reduced by every received packet
                packet.length = BUFFER_SIZE
                socket.receive(packet)
            } catch (_: SocketTimeoutException) {
                return
            }
            val now = SystemClock.elapsedRealtime()
            lastMessageTime = now
            if (BinaryProtocol.isJob(packet.data, packet.length)) {
                listener.onJob(BinaryProtocol.decodeJob(packet.data, packet.length))
            } else if (BinaryProtocol.isUnboundActions(packet.data, packet.length)) {
                listener.onUnboundKeys(BinaryProtocol.decodeUnboundActions(packet.data, packet.length))
            } else {
                ControllerProtocol.decodeServerMessage(packet.data, packet.length)?.let {
                    meter.onMessage(now, it.sequence)
                    lastServerState = it
                    listener.onServerState(it)
                }
            }
            if (resuming) {
                resuming = false
                report(ConnectionState.Connected)
            }
        }

        private fun reportLinkQuality(now: Long) {
            meter.quality(now)?.let {
                lastLinkQuality = it
                if (isReportable) listener.onLinkQuality(it)
            }
        }

        private fun receiveMessages() {
            val packet = DatagramPacket(ByteArray(BUFFER_SIZE), BUFFER_SIZE)
            var lastQualityReport = 0L

            while (running) {
                receiveMessage(packet)

                val now = SystemClock.elapsedRealtime()
                // The server doesn't send anything to a paused controller
                if (isPaused || isPausedByUser) {
                    lastMessageTime = now
                    meter.reset()
                    continue
                }
                val silence = now - lastMessageTime
                if (silence > LOST_AFTER_MS) return
                if (silence > RESUME_AFTER_MS && !resuming) {
                    resuming = true
                    report(ConnectionState.Resuming)
                }
                if (now - lastQualityReport >= QUALITY_REPORT_INTERVAL_MS) {
                    lastQualityReport = now
                    reportLinkQuality(now)
                }
            }
        }

        private fun send(text: String) = send(text.toByteArray())

        private fun send(bytes: ByteArray) {
            socket.send(DatagramPacket(bytes, bytes.size))
        }
    }

    companion object {
        // 1 s, 2 s, 4 s, then 5 s between reconnection attempts (an attempt is only a few small packets)
        fun reconnectDelayMs(failedAttempts: Int): Long =
            (RECONNECT_START_MS shl failedAttempts.coerceAtMost(MAX_RECONNECT_SHIFT)).coerceAtMost(RECONNECT_MAX_MS)

        private const val HANDSHAKE_TIMEOUT_MS = 600
        private const val HELLO_ATTEMPTS = 3
        private const val RECEIVE_TIMEOUT_MS = 100

        // About 60 times per second
        private const val SEND_INTERVAL_MS = 16L
        private const val PAUSED_INTERVAL_MS = 500L

        // The server releases the controls after 1.2 s of silence, so the session is resumed earlier
        private const val RESUME_AFTER_MS = 500L
        private const val HELLO_EVERY_N_MESSAGES = 20L
        private const val LOST_AFTER_MS = 3_000L

        private const val QUALITY_REPORT_INTERVAL_MS = 1_000L
        private const val RECONNECT_START_MS = 1_000L
        private const val RECONNECT_MAX_MS = 5_000L
        private const val MAX_RECONNECT_SHIFT = 4
        private const val BUFFER_SIZE = 256
        private const val NANOS_IN_MS = 1_000_000L
    }
}

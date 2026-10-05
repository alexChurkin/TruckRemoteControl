package com.alexchurkin.truckremote.ui.main

import com.alexchurkin.truckremote.R
import com.alexchurkin.truckremote.data.analytics.Analytics
import com.alexchurkin.truckremote.data.controller.ConnectionState
import com.alexchurkin.truckremote.data.controller.ControllerAction
import com.alexchurkin.truckremote.data.controller.ControllerRepository
import com.alexchurkin.truckremote.data.controller.ControllerState
import com.alexchurkin.truckremote.data.controller.HornState
import com.alexchurkin.truckremote.data.controller.LinkQuality
import com.alexchurkin.truckremote.data.controller.ServerState
import com.alexchurkin.truckremote.data.device.Haptics
import com.alexchurkin.truckremote.data.device.WifiStatus
import com.alexchurkin.truckremote.data.sensor.TiltReading
import com.alexchurkin.truckremote.data.sensor.TiltSensor
import com.alexchurkin.truckremote.data.settings.AppSettings
import com.alexchurkin.truckremote.data.settings.PedalMode
import com.alexchurkin.truckremote.domain.SteeringCurve
import com.alexchurkin.truckremote.testing.FakeSharedPreferences
import kotlin.math.pow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val controller = FakeController()
    private val settings = AppSettings(FakeSharedPreferences())
    private val tilt = FakeTiltSensor()
    private val vibrations = mutableListOf<Long>()
    private val events = mutableListOf<String>()
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = createViewModel()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun createViewModel() = MainViewModel(
        controller = controller,
        settings = settings,
        tiltSensor = tilt,
        haptics = object : Haptics {
            override fun vibrate(durationMs: Long) {
                vibrations += durationMs
            }
        },
        wifi = object : WifiStatus {
            override val isEnabled = true
            override val rssi = -50
        },
        analytics = object : Analytics {
            override fun report(event: String, params: Map<String, Any>) {
                events += event
            }
        },
    ).also { it.setThrottleLockDistance(LOCK_DISTANCE) }

    private fun TestScope.collectEffects(): MutableList<MainEffect> {
        val effects = mutableListOf<MainEffect>()
        backgroundScope.launch(dispatcher) { viewModel.effects.toList(effects) }
        return effects
    }

    private fun connect(truck: ServerState = TRUCK) {
        controller.connectionState.value = ConnectionState.Connected
        controller.truckState.value = truck
    }

    @Test
    fun `search connects asking the known server and remembers the found one`() = runTest {
        settings.lastServerIp = "192.168.1.5"
        val effects = collectEffects()

        viewModel.start(releaseNotesVersion = 1)
        assertEquals(null to "192.168.1.5", controller.lastConnect)

        controller.serverAddress = "192.168.1.7"
        connect()

        assertEquals("192.168.1.7", settings.lastServerIp)
        assertTrue(MainEffect.Message(R.string.connected_to_server_at, "192.168.1.7") in effects)
        assertEquals(listOf(Analytics.EVENT_SERVER_CONNECTED), events)
    }

    @Test
    fun `first start shows the guide, a new version shows release notes`() {
        assertEquals(StartAction.Guide, viewModel.start(releaseNotesVersion = 3))
        assertEquals(StartAction.None, viewModel.start(releaseNotesVersion = 3))
        assertEquals(StartAction.ReleaseNotes, viewModel.start(releaseNotesVersion = 4))
        viewModel.onReleaseNotesShown(4)
        assertEquals(StartAction.None, viewModel.start(releaseNotesVersion = 4))
    }

    @Test
    fun `wrong server address isn't used`() = runTest {
        settings.useSpecifiedServer = true
        settings.specifiedServerIp = "192.168.1"
        val effects = collectEffects()

        viewModel.connect(useSpecifiedServer = true)

        assertNull(controller.lastConnect)
        assertTrue(MainEffect.Message(R.string.def_server_ip_not_correct) in effects)
    }

    @Test
    fun `hold action is held only while connected, but is always released`() {
        assertFalse(viewModel.onActionHold(ControllerAction.EngineBrake, true))
        assertTrue(controller.state.heldActions.isEmpty())

        connect()
        assertTrue(viewModel.onActionHold(ControllerAction.EngineBrake, true))
        assertEquals(setOf(ControllerAction.EngineBrake), controller.state.heldActions)

        controller.connectionState.value = ConnectionState.Lost
        assertTrue(viewModel.onActionHold(ControllerAction.EngineBrake, false))
        assertTrue(controller.state.heldActions.isEmpty())
    }

    @Test
    fun `buttons work only when connected and not paused`() {
        viewModel.onLeftSignal()
        assertFalse(viewModel.onAction(ControllerAction.Engine))
        assertFalse(controller.state.leftSignalClick)

        connect()
        viewModel.onLeftSignal()
        assertTrue(viewModel.onAction(ControllerAction.Engine))
        assertTrue(controller.state.leftSignalClick)
        assertEquals(1, controller.state.actionCounters[ControllerAction.Engine])

        viewModel.togglePause()
        viewModel.onLeftSignal()
        assertTrue(controller.state.leftSignalClick)
        assertTrue(controller.pausedByUserNow)
    }

    @Test
    fun `pausing releases the pedals and shows an ad`() = runTest {
        val effects = collectEffects()
        connect()
        viewModel.onPedalDown(Pedal.Gas, 0f, 500f, 1000)
        assertTrue(controller.state.gasPressed)

        viewModel.togglePause()

        assertFalse(controller.state.gasPressed)
        assertTrue(viewModel.state.value.pausedByUser)
        assertTrue(MainEffect.ShowAd in effects)
    }

    @Test
    fun `digital pedals are keys`() {
        connect()
        viewModel.onPedalDown(Pedal.Brake, 0f, 500f, 1000)

        assertTrue(controller.state.brakePressed)
        assertEquals(0f, controller.state.brakeLevel)
        assertEquals(1f, viewModel.state.value.pedals.brakeLevel)

        viewModel.onPedalUp(Pedal.Brake)
        assertFalse(controller.state.brakePressed)
    }

    @Test
    fun `analog pedals are levels when the server supports them, keys otherwise`() {
        settings.pedalMode = PedalMode.Analog
        connect(TRUCK.copy(analogPedalsAvailable = false))
        viewModel.onPedalDown(Pedal.Gas, 0f, 500f, 1000)
        assertTrue(controller.state.gasPressed)

        // The server starts supporting axes: the same press is sent as a level
        controller.truckState.value = TRUCK.copy(analogPedalsAvailable = true)
        assertFalse(controller.state.gasPressed)
        assertEquals(0f, controller.state.gasLevel)
        viewModel.onPedalMove(Pedal.Gas, 0f, 380f)
        assertEquals(0.2f, controller.state.gasLevel, 0.001f)
    }

    @Test
    fun `analog pedals unavailable on the server are reported once`() = runTest {
        settings.pedalMode = PedalMode.Analog
        val effects = collectEffects()
        connect(TRUCK.copy(analogPedalsAvailable = false))

        viewModel.onPedalDown(Pedal.Gas, 0f, 500f, 1000)
        viewModel.onPedalUp(Pedal.Gas)
        viewModel.onPedalDown(Pedal.Gas, 0f, 500f, 1000)

        assertEquals(1, effects.count { it == MainEffect.Message(R.string.analog_pedals_unavailable) })
    }

    @Test
    fun `throttle lock is off by default`() {
        settings.pedalMode = PedalMode.Analog
        connect(TRUCK.copy(analogPedalsAvailable = true))
        viewModel.onPedalDown(Pedal.Gas, 0f, 500f, 1000)
        viewModel.onPedalMove(Pedal.Gas, 0f, 200f)
        viewModel.onPedalMove(Pedal.Gas, LOCK_DISTANCE + 1, 200f)
        viewModel.onPedalUp(Pedal.Gas)

        assertFalse(viewModel.state.value.pedals.gasLocked)
        assertEquals(0f, controller.state.gasLevel)
    }

    @Test
    fun `brake releases the locked throttle`() = runTest {
        settings.pedalMode = PedalMode.Analog
        settings.throttleLock = true
        val effects = collectEffects()
        connect(TRUCK.copy(analogPedalsAvailable = true))
        viewModel.onPedalDown(Pedal.Gas, 0f, 500f, 1000)
        viewModel.onPedalMove(Pedal.Gas, 0f, 200f)
        viewModel.onPedalMove(Pedal.Gas, LOCK_DISTANCE + 1, 200f)
        viewModel.onPedalUp(Pedal.Gas)
        assertTrue(viewModel.state.value.pedals.gasLocked)
        assertEquals(0.5f, controller.state.gasLevel, 0.001f)
        assertTrue(MainEffect.ThrottleLockChanged in effects)

        viewModel.onPedalDown(Pedal.Brake, 0f, 500f, 1000)

        assertFalse(viewModel.state.value.pedals.gasLocked)
        assertEquals(0f, controller.state.gasLevel)
    }

    @Test
    fun `lost connection releases the pedals and is reported`() = runTest {
        val effects = collectEffects()
        connect()
        viewModel.onPedalDown(Pedal.Gas, 0f, 500f, 1000)

        controller.connectionState.value = ConnectionState.Lost

        assertFalse(controller.state.gasPressed)
        assertTrue(MainEffect.Message(R.string.connection_lost) in effects)
    }

    @Test
    fun `resumed session isn't reported as a new connection`() = runTest {
        val effects = collectEffects()
        connect()
        controller.connectionState.value = ConnectionState.Resuming
        controller.connectionState.value = ConnectionState.Connected

        assertEquals(1, effects.count { it is MainEffect.Message && it.text == R.string.connected_to_server_at })
        assertTrue(viewModel.state.value.isConnected)
    }

    @Test
    fun `server not found is shown once per connection attempt`() = runTest {
        val effects = collectEffects()
        viewModel.connect(useSpecifiedServer = false)
        controller.connectionState.value = ConnectionState.NotFound
        controller.connectionState.value = ConnectionState.Searching
        controller.connectionState.value = ConnectionState.NotFound

        assertEquals(1, effects.count { it == MainEffect.ServerNotFound })
    }

    @Test
    fun `tilt steers only on screen while connected, with calibration and curve`() {
        // Full lock at 90°: the steering value is the gravity projection
        settings.steeringMaxAngle = 90
        tilt.readings.tryEmit(TiltReading(3f, reverseLandscape = false))
        assertEquals(0f, controller.state.steering)

        viewModel.setForeground(true)
        connect()
        // The default curve is smoother near the center (exponent 1.5)
        val g = SteeringCurve.GRAVITY
        tilt.readings.tryEmit(TiltReading(3f, reverseLandscape = false))
        assertEquals((3f / g).pow(1.5f) * g, controller.state.steering, 0.001f)

        settings.steeringExponent = 1f
        tilt.readings.tryEmit(TiltReading(3f, reverseLandscape = false))
        assertEquals(3f, controller.state.steering, 0.001f)

        tilt.readings.tryEmit(TiltReading(3f, reverseLandscape = true))
        assertEquals(-3f, controller.state.steering, 0.001f)

        viewModel.calibrate()
        tilt.readings.tryEmit(TiltReading(3f, reverseLandscape = false))
        assertEquals(0f, controller.state.steering, 0.001f)

        settings.steeringDeadZone = 15
        viewModel.resetCalibration()
        tilt.readings.tryEmit(TiltReading(1f, reverseLandscape = false))
        assertEquals(0f, controller.state.steering, 0.001f)

        viewModel.setForeground(false)
        assertEquals(0, tilt.subscriptions.value)
    }

    @Test
    fun `force feedback vibrates only when it is turned on`() {
        controller.forceFeedbackFlow.tryEmit(100)
        assertEquals(emptyList<Long>(), vibrations)

        settings.forceFeedback = true
        controller.forceFeedbackFlow.tryEmit(120)
        assertEquals(listOf(120L), vibrations)
    }

    @Test
    fun `horn uses the chosen sound`() {
        assertFalse(viewModel.onHorn(pressed = true))
        connect()
        settings.pneumaticHorn = true

        assertTrue(viewModel.onHorn(pressed = true))
        assertEquals(HornState.Pneumatic, controller.state.horn)
        viewModel.onHorn(pressed = false)
        assertEquals(HornState.Off, controller.state.horn)
    }

    @Test
    fun `screen is closed - connection is closed`() {
        viewModel.start(releaseNotesVersion = 1)
        val method = MainViewModel::class.java.getDeclaredMethod("onCleared").apply { isAccessible = true }
        method.invoke(viewModel)
        assertTrue(controller.disconnected)
    }

    private class FakeController : ControllerRepository {
        var state = ControllerState()
        var lastConnect: Pair<String?, String?>? = null
        var pausedByUserNow = false
        var disconnected = false
        val forceFeedbackFlow = MutableSharedFlow<Long>(extraBufferCapacity = 4)

        override val connectionState = MutableStateFlow(ConnectionState.Disconnected)
        override val truckState = MutableStateFlow<ServerState?>(null)
        override val forceFeedback: Flow<Long> = forceFeedbackFlow
        override val linkQuality = MutableStateFlow<LinkQuality?>(null)
        override var serverAddress: String? = null

        override fun connect(ip: String?, port: Int, knownIp: String?) {
            lastConnect = ip to knownIp
        }

        override fun disconnect() {
            disconnected = true
        }

        override fun setForeground(foreground: Boolean) = Unit

        override fun setPausedByUser(paused: Boolean) {
            pausedByUserNow = paused
        }

        override fun updateState(transform: (ControllerState) -> ControllerState) {
            state = transform(state)
        }

        override fun clickAction(action: ControllerAction) = updateState { it.withClick(action) }

        override fun setActionHeld(action: ControllerAction, held: Boolean) = updateState { it.withHeld(action, held) }
    }

    private class FakeTiltSensor : TiltSensor {
        val readings = MutableSharedFlow<TiltReading>(extraBufferCapacity = 4)
        val subscriptions = readings.subscriptionCount
        override val kind = "gravity"

        override fun readings(): Flow<TiltReading> = readings
    }

    private companion object {
        const val LOCK_DISTANCE = 100f
        val TRUCK = ServerState(
            engineOn = true,
            parkingBrake = false,
            leftBlinker = false,
            rightBlinker = false,
            lightsMode = 0,
            ffbDurationMs = 0,
        )
    }
}

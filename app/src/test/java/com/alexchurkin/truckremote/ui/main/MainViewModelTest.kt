package com.alexchurkin.truckremote.ui.main

import com.alexchurkin.truckremote.R
import com.alexchurkin.truckremote.data.analytics.Analytics
import com.alexchurkin.truckremote.data.controller.BinaryProtocol
import com.alexchurkin.truckremote.data.controller.ConnectionState
import com.alexchurkin.truckremote.data.controller.ControllerAction
import com.alexchurkin.truckremote.data.controller.ControllerRepository
import com.alexchurkin.truckremote.data.controller.ControllerState
import com.alexchurkin.truckremote.data.controller.Dashboard
import com.alexchurkin.truckremote.data.controller.Feedback
import com.alexchurkin.truckremote.data.controller.HapticEvent
import com.alexchurkin.truckremote.data.controller.HornState
import com.alexchurkin.truckremote.data.controller.LinkQuality
import com.alexchurkin.truckremote.data.controller.RoadFeel
import com.alexchurkin.truckremote.data.controller.RoadSurface
import com.alexchurkin.truckremote.data.controller.ServerState
import com.alexchurkin.truckremote.data.device.HapticCapability
import com.alexchurkin.truckremote.data.device.Haptics
import com.alexchurkin.truckremote.data.device.WifiStatus
import com.alexchurkin.truckremote.data.sensor.TiltReading
import com.alexchurkin.truckremote.data.sensor.TiltSensor
import com.alexchurkin.truckremote.data.settings.ActionLayout
import com.alexchurkin.truckremote.data.settings.AppSettings
import com.alexchurkin.truckremote.data.settings.Game
import com.alexchurkin.truckremote.data.settings.PedalMode
import com.alexchurkin.truckremote.data.settings.SpeedUnits
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
import org.junit.Assert.assertNotNull
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
    private val vibrations = mutableListOf<String>()
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
            override val capability = HapticCapability.Amplitude

            override fun play(event: HapticEvent, strength: Float, intensity: Float) {
                vibrations += "$event $strength $intensity"
            }

            override fun pulse(durationMs: Long, intensity: Float) {
                vibrations += "pulse $durationMs $intensity"
            }

            override fun setRoad(feel: RoadFeel, intensity: Float) {
                vibrations += "road ${feel.level} ${feel.surface} $intensity"
            }

            override fun stop() {
                vibrations += "stop"
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
        // Every start of the app has its own view model
        assertEquals(StartAction.None, createViewModel().start(releaseNotesVersion = 3))
        val updated = createViewModel()
        assertEquals(StartAction.ReleaseNotes, updated.start(releaseNotesVersion = 4))
        updated.onReleaseNotesShown(4)
        assertEquals(StartAction.None, createViewModel().start(releaseNotesVersion = 4))
    }

    @Test
    fun `screen recreated with its view model doesn't connect again, a restored one connects`() {
        viewModel.start(releaseNotesVersion = 3)
        assertNotNull(controller.lastConnect)

        // Rotation: the same view model
        controller.lastConnect = null
        assertEquals(StartAction.None, viewModel.start(releaseNotesVersion = 3))
        assertNull(controller.lastConnect)

        // The app was killed in background: the restored screen gets a new view model
        createViewModel().start(releaseNotesVersion = 3)
        assertNotNull(controller.lastConnect)
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
        // A tap is held for a moment
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(controller.state.heldActions.isEmpty())
    }

    @Test
    fun `the light horn is held by a new server and clicked by an older one`() {
        connect()
        assertTrue(viewModel.onActionHold(ControllerAction.LightHorn, true))
        assertEquals(setOf(ControllerAction.LightHorn), controller.state.heldActions)
        assertTrue(viewModel.onActionHold(ControllerAction.LightHorn, false))
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(controller.state.heldActions.isEmpty())

        connect(TRUCK.copy(serverRevision = ControllerAction.LightHorn.holdRevision - 1))
        assertTrue(viewModel.onActionHold(ControllerAction.LightHorn, true))
        assertTrue(viewModel.onActionHold(ControllerAction.LightHorn, false))
        assertTrue(controller.state.heldActions.isEmpty())
        assertEquals(1, controller.state.actionCounters[ControllerAction.LightHorn])
    }

    @Test
    fun `the specified server found at another address is remembered there`() {
        settings.useSpecifiedServer = true
        settings.specifiedServerIp = "192.168.1.8"
        viewModel.connect(useSpecifiedServer = true)

        controller.serverAddress = "192.168.1.6"
        connect()

        assertEquals("192.168.1.6", settings.specifiedServerIp)
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
        viewModel.onPedalDown(Pedal.Gas, 0f, 500f, 1500)
        assertTrue(controller.state.gasPressed)

        viewModel.togglePause()

        assertFalse(controller.state.gasPressed)
        assertTrue(viewModel.state.value.pausedByUser)
        assertTrue(MainEffect.ShowAd in effects)
    }

    @Test
    fun `digital pedals are keys`() {
        settings.pedalMode = PedalMode.Digital
        connect()
        viewModel.onPedalDown(Pedal.Brake, 0f, 500f, 1500)

        assertTrue(controller.state.brakePressed)
        assertEquals(0f, controller.state.brakeLevel)
        assertEquals(1f, viewModel.state.value.pedals.brakeLevel)

        viewModel.onPedalUp(Pedal.Brake)
        assertFalse(controller.state.brakePressed)
    }

    @Test
    fun `pedals are analog by default, levels when the server supports them, keys otherwise`() {
        connect(TRUCK.copy(analogPedalsAvailable = false))
        viewModel.onPedalDown(Pedal.Gas, 0f, 500f, 1500)
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

        viewModel.onPedalDown(Pedal.Gas, 0f, 500f, 1500)
        viewModel.onPedalUp(Pedal.Gas)
        viewModel.onPedalDown(Pedal.Gas, 0f, 500f, 1500)

        assertEquals(1, effects.count { it == MainEffect.Message(R.string.analog_pedals_unavailable) })
    }

    @Test
    fun `a released analog pedal returns by its spring, a pause drops it at once`() {
        settings.throttleLock = false
        connect(TRUCK.copy(analogPedalsAvailable = true))
        viewModel.onPedalDown(Pedal.Gas, 0f, 500f, 1500)
        viewModel.onPedalMove(Pedal.Gas, 0f, 200f)
        viewModel.onPedalUp(Pedal.Gas)
        assertEquals(0.5f, controller.state.gasLevel, 0.001f)

        dispatcher.scheduler.advanceTimeBy(50)
        val returning = controller.state.gasLevel
        assertTrue(returning > 0f && returning < 0.5f)
        assertEquals(returning, viewModel.state.value.pedals.gasLevel, 0.001f)

        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(0f, controller.state.gasLevel)

        viewModel.onPedalDown(Pedal.Gas, 0f, 500f, 1500)
        viewModel.onPedalMove(Pedal.Gas, 0f, 200f)
        viewModel.togglePause()
        assertEquals(0f, controller.state.gasLevel)
    }

    @Test
    fun `throttle lock can be turned off`() {
        settings.throttleLock = false
        connect(TRUCK.copy(analogPedalsAvailable = true))
        viewModel.onPedalDown(Pedal.Gas, 0f, 500f, 1500)
        viewModel.onPedalMove(Pedal.Gas, 0f, 200f)
        viewModel.onPedalMove(Pedal.Gas, -LOCK_DISTANCE - 1, 200f)
        viewModel.onPedalUp(Pedal.Gas)

        assertFalse(viewModel.state.value.pedals.gasLocked)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(0f, controller.state.gasLevel)
    }

    @Test
    fun `throttle is locked by default and the brake releases it`() = runTest {
        val effects = collectEffects()
        connect(TRUCK.copy(analogPedalsAvailable = true))
        viewModel.onPedalDown(Pedal.Gas, 0f, 500f, 1500)
        viewModel.onPedalMove(Pedal.Gas, 0f, 200f)
        viewModel.onPedalMove(Pedal.Gas, -LOCK_DISTANCE - 1, 200f)
        viewModel.onPedalUp(Pedal.Gas)
        assertTrue(viewModel.state.value.pedals.gasLocked)
        assertEquals(0.5f, controller.state.gasLevel, 0.001f)
        assertTrue(MainEffect.ThrottleLockChanged in effects)

        viewModel.onPedalDown(Pedal.Brake, 0f, 500f, 1500)

        assertFalse(viewModel.state.value.pedals.gasLocked)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(0f, controller.state.gasLevel)
    }

    @Test
    fun `lost connection releases the pedals and is reported`() = runTest {
        val effects = collectEffects()
        connect()
        viewModel.onPedalDown(Pedal.Gas, 0f, 500f, 1500)

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
    fun `panel layout is edited and reset`() {
        viewModel.onActionLayoutChange(ActionLayout.Default.with(page = 4, slot = 7, action = ControllerAction.Engine))

        assertEquals(ControllerAction.Engine, viewModel.state.value.actionLayout.pages[4][7])
        assertNull(viewModel.state.value.actionLayout.pages[0][4])
        assertEquals(viewModel.state.value.actionLayout, settings.game(Game.Ets2).actionLayout)

        viewModel.onActionLayoutChange(ActionLayout.Default)
        assertEquals(ActionLayout.Default, viewModel.state.value.actionLayout)
    }

    @Test
    fun `outdated server hint is shown once`() = runTest {
        val effects = collectEffects()
        connect(TRUCK.copy(serverRevision = 2))
        controller.truckState.value = TRUCK.copy(serverRevision = 2, engineOn = true)
        assertEquals(1, effects.count { it == MainEffect.ServerOutdated })

        // Not again after the app is restarted
        controller.connectionState.value = ConnectionState.Disconnected
        viewModel = createViewModel()
        val laterEffects = collectEffects()
        connect(TRUCK.copy(serverRevision = 1))
        assertEquals(0, laterEffects.count { it == MainEffect.ServerOutdated })
    }

    @Test
    fun `up to date server gets no hint`() = runTest {
        val effects = collectEffects()
        connect(TRUCK.copy(serverRevision = BinaryProtocol.REVISION))
        assertEquals(0, effects.count { it == MainEffect.ServerOutdated })
    }

    @Test
    fun `settings of the game being played are used`() {
        settings.separateGameSettings = true
        settings.game(Game.Ats).speedUnits = SpeedUnits.Metric
        settings.game(Game.Ats).actionLayout = ActionLayout.Default.with(0, 0, ControllerAction.Map)

        connect(TRUCK.copy(dashboard = DASHBOARD.copy(isAts = true)))

        assertEquals(Game.Ats, settings.lastGame)
        assertFalse(viewModel.state.value.imperialUnits)
        assertEquals(ControllerAction.Map, viewModel.state.value.actionLayout.pages[0][0])

        controller.truckState.value = TRUCK.copy(dashboard = DASHBOARD)
        assertEquals(Game.Ets2, settings.lastGame)
        assertEquals(ControllerAction.EngineBrake, viewModel.state.value.actionLayout.pages[0][0])
    }

    @Test
    fun `speed units follow the game by default`() {
        connect(TRUCK.copy(dashboard = DASHBOARD.copy(isAts = true)))
        assertTrue(viewModel.state.value.imperialUnits)

        settings.game(Game.Ats).speedUnits = SpeedUnits.Metric
        assertFalse(viewModel.state.value.imperialUnits)
    }

    @Test
    fun `phone screen down pauses the controller until it is picked up`() {
        viewModel.setForeground(true)
        connect()
        viewModel.onPedalDown(Pedal.Gas, 0f, 0f, 100)
        assertTrue(controller.state.gasPressed)

        tilt.readings.tryEmit(TiltReading(0f, 0, screenUp = -0.9f))

        assertEquals(AutoPause.FaceDown, viewModel.state.value.autoPause)
        assertTrue(controller.pausedByUserNow)
        assertFalse(controller.state.gasPressed)
        assertFalse(viewModel.onAction(ControllerAction.Engine))

        // Still down a bit: the hysteresis keeps the pause
        tilt.readings.tryEmit(TiltReading(0f, 1, screenUp = -0.55f))
        assertEquals(AutoPause.FaceDown, viewModel.state.value.autoPause)

        tilt.readings.tryEmit(TiltReading(0f, 2, screenUp = 0.3f))
        assertNull(viewModel.state.value.autoPause)
        assertFalse(controller.pausedByUserNow)
    }

    @Test
    fun `phone lying screen up pauses the controller until it is picked up`() {
        viewModel.setForeground(true)
        connect()

        // Held as a wheel, tilted back a lot: no pause
        tilt.readings.tryEmit(TiltReading(0f, 0, screenUp = 0.9f))
        assertNull(viewModel.state.value.autoPause)

        tilt.readings.tryEmit(TiltReading(0f, 1, screenUp = 0.99f))
        assertEquals(AutoPause.FaceUp, viewModel.state.value.autoPause)
        assertTrue(controller.pausedByUserNow)

        // Still almost flat: the hysteresis keeps the pause
        tilt.readings.tryEmit(TiltReading(0f, 2, screenUp = 0.95f))
        assertEquals(AutoPause.FaceUp, viewModel.state.value.autoPause)

        tilt.readings.tryEmit(TiltReading(0f, 3, screenUp = 0.5f))
        assertNull(viewModel.state.value.autoPause)
        assertFalse(controller.pausedByUserNow)
    }

    @Test
    fun `cruise control that did not turn on is reported`() = runTest {
        val effects = collectEffects()
        connect(TRUCK.copy(dashboard = DASHBOARD.copy(speed = 3f, cruiseSpeed = 0f)))

        assertTrue(viewModel.onCruiseToggle())
        dispatcher.scheduler.advanceTimeBy(2000)
        assertTrue(MainEffect.Message(R.string.cruise_not_engaged_slow) in effects)

        // Fast enough and the game turned it on: nothing is reported
        effects.clear()
        controller.truckState.value = TRUCK.copy(dashboard = DASHBOARD.copy(speed = 20f, cruiseSpeed = 0f))
        assertTrue(viewModel.onCruiseToggle())
        controller.truckState.value = TRUCK.copy(dashboard = DASHBOARD.copy(speed = 20f, cruiseSpeed = 20f))
        dispatcher.scheduler.advanceTimeBy(2000)
        assertTrue(effects.none { it is MainEffect.Message })
    }

    @Test
    fun `auto pause can be turned off`() {
        settings.autoPause = false
        viewModel.setForeground(true)
        connect()

        tilt.readings.tryEmit(TiltReading(0f, 0, screenUp = -0.9f))
        assertNull(viewModel.state.value.autoPause)

        tilt.readings.tryEmit(TiltReading(0f, 1, screenUp = 0.99f))
        assertNull(viewModel.state.value.autoPause)
        assertFalse(controller.pausedByUserNow)
    }

    @Test
    fun `silent tilt sensor pauses the controller`() {
        viewModel.setForeground(true)
        connect()
        tilt.readings.tryEmit(TiltReading(0f, 0, screenUp = 0.3f))

        dispatcher.scheduler.advanceTimeBy(1500)
        assertEquals(AutoPause.NoSensor, viewModel.state.value.autoPause)
        assertTrue(controller.pausedByUserNow)

        tilt.readings.tryEmit(TiltReading(0f, 1, screenUp = 0.3f))
        assertNull(viewModel.state.value.autoPause)
        assertFalse(controller.pausedByUserNow)
    }

    @Test
    fun `tilt steers only on screen while connected, with calibration and curve`() {
        // A second between the readings: the filter starts anew and passes them as is
        var time = 0L
        fun tiltTo(angle: Float) = tilt.readings.tryEmit(TiltReading(angle, time++ * 1_000_000_000L))
        val g = SteeringCurve.GRAVITY
        settings.game(Game.Ets2).steeringMaxAngle = 90
        tiltTo(30f)
        assertEquals(0f, controller.state.steering)

        viewModel.setForeground(true)
        connect()
        // The default curve is smoother near the center (exponent 1.5)
        tiltTo(30f)
        assertEquals((1f / 3).pow(1.5f) * g, controller.state.steering, 0.001f)

        settings.game(Game.Ets2).steeringExponent = 1f
        tiltTo(30f)
        assertEquals(g / 3, controller.state.steering, 0.001f)
        tiltTo(-30f)
        assertEquals(-g / 3, controller.state.steering, 0.001f)

        // The current position becomes straight
        tiltTo(30f)
        viewModel.calibrate()
        tiltTo(30f)
        assertEquals(0f, controller.state.steering, 0.001f)

        settings.game(Game.Ets2).steeringDeadZone = 15
        viewModel.resetCalibration()
        tiltTo(10f)
        assertEquals(0f, controller.state.steering, 0.001f)

        viewModel.setForeground(false)
        assertEquals(0, tilt.subscriptions.value)
    }

    @Test
    fun `vibration is felt only while it is on and the truck is controlled`() {
        settings.forceFeedback = false
        viewModel.setForeground(true)
        connect()
        controller.feedbackFlow.tryEmit(Feedback.Pulse(100))
        assertEquals(emptyList<String>(), vibrations.filterNot { it.startsWith("road") })

        settings.forceFeedback = true
        settings.vibrationStrength = 50
        controller.feedbackFlow.tryEmit(Feedback.Pulse(120))
        controller.feedbackFlow.tryEmit(Feedback.Event(HapticEvent.Collision, 0.8f))
        assertEquals(listOf("pulse 120 0.5", "Collision 0.8 0.5"), vibrations.filterNot { it.startsWith("road") })

        viewModel.togglePause()
        vibrations.clear()
        controller.feedbackFlow.tryEmit(Feedback.Event(HapticEvent.Collision, 1f))
        assertEquals(emptyList<String>(), vibrations.filterNot { it.startsWith("road") })
    }

    @Test
    fun `dashboard clicks can be turned off`() {
        settings.forceFeedback = true
        settings.dashboardClicks = false
        viewModel.setForeground(true)
        connect()
        controller.feedbackFlow.tryEmit(Feedback.Event(HapticEvent.Blinker, 1f))
        controller.feedbackFlow.tryEmit(Feedback.Event(HapticEvent.TrailerCoupled, 1f))

        assertEquals(listOf("TrailerCoupled 1.0 0.7"), vibrations.filterNot { it.startsWith("road") })
    }

    @Test
    fun `road vibrates while the screen is shown and the truck is controlled`() {
        settings.forceFeedback = true
        connect()
        controller.road.value = RoadFeel(0.5f, RoadSurface.Offroad)
        assertEquals("road 0.0 Road 0.0", vibrations.last())

        viewModel.setForeground(true)
        assertEquals("road 0.5 Offroad 0.7", vibrations.last())

        settings.roadVibration = false
        assertEquals("road 0.0 Road 0.0", vibrations.last())
        settings.roadVibration = true
        viewModel.setForeground(false)
        assertEquals("road 0.0 Road 0.0", vibrations.last())
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
        val feedbackFlow = MutableSharedFlow<Feedback>(extraBufferCapacity = 4)

        override val connectionState = MutableStateFlow(ConnectionState.Disconnected)
        override val truckState = MutableStateFlow<ServerState?>(null)
        override val feedback: Flow<Feedback> = feedbackFlow
        override val road = MutableStateFlow(RoadFeel.None)
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
        val DASHBOARD = Dashboard(
            speed = 20f,
            speedLimit = 0f,
            cruiseSpeed = 0f,
            gear = 8,
            engineRpm = 1200,
            engineRpmMax = 2500,
            fuelPercent = 50,
            isAts = false,
        )

        val TRUCK = ServerState(
            engineOn = true,
            parkingBrake = false,
            leftBlinker = false,
            rightBlinker = false,
            lightsMode = 0,
            ffbDurationMs = 0,
            serverRevision = BinaryProtocol.REVISION,
        )
    }
}

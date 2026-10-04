package com.alexchurkin.truckremote.ui.main

import android.annotation.SuppressLint
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.TypedValue
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.annotation.AnimRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.alexchurkin.truckremote.R
import com.alexchurkin.truckremote.analytics.Analytics
import com.alexchurkin.truckremote.app
import com.alexchurkin.truckremote.control.PedalHandler
import com.alexchurkin.truckremote.control.SteeringCurve
import com.alexchurkin.truckremote.databinding.ActivityMainBinding
import com.alexchurkin.truckremote.net.ControllerAction
import com.alexchurkin.truckremote.net.HornState
import com.alexchurkin.truckremote.net.LowLatencyWifiLock
import com.alexchurkin.truckremote.net.ServerState
import com.alexchurkin.truckremote.net.TrackingClient
import com.alexchurkin.truckremote.settings.PedalMode
import com.alexchurkin.truckremote.ui.guide.GuideActivity
import com.alexchurkin.truckremote.ui.settings.SettingsActivity
import com.alexchurkin.truckremote.util.Toaster
import com.alexchurkin.truckremote.util.enterFullscreen
import com.alexchurkin.truckremote.util.isReverseLandscape
import com.alexchurkin.truckremote.util.isValidIpv4
import com.alexchurkin.truckremote.util.showKeepingFullscreen
import kotlin.math.abs
import kotlin.math.roundToInt

class MainActivity :
    AppCompatActivity(),
    SensorEventListener,
    TrackingClient.Listener,
    PedalHandler.Listener,
    MenuDialogFragment.Listener {

    private lateinit var binding: ActivityMainBinding
    private val settings by lazy { app.settings }
    private val client = TrackingClient(this)

    private lateinit var sensorManager: SensorManager
    private var tiltSensor: Sensor? = null
    private val wifiLock by lazy { LowLatencyWifiLock(this) }
    private val vibrator: Vibrator by lazy { obtainVibrator() }

    private lateinit var brakePedal: PedalHandler
    private lateinit var gasPedal: PedalHandler
    private lateinit var cruiseGestureDetector: GestureDetector

    private var isConnected = false
    private var searchingByBroadcast = false

    @Volatile
    private var shownServerState: ServerState? = null

    // Without calibration offset
    private var lastRawTiltY = 0f
    private var analogUnavailableWarned = false

    // Settings are cached on resume: sensor events come very often
    private var calibrationOffset = 0f
    private var steeringCurve = SteeringCurve(deadZoneDeg = 0, maxAngleDeg = 90, exponent = 1f)

    @Volatile
    private var useForceFeedback = false
    private var analogPedalsMode = false

    // Every view has its own animation instances: one instance can't run on several views
    private val animations = HashMap<Pair<Int, Int>, Animation>()

    private val actionViews by lazy {
        mapOf(
            binding.actionEngine to ControllerAction.Engine,
            binding.actionTrailer to ControllerAction.Trailer,
            binding.actionActivate to ControllerAction.Activate,
            binding.actionLightHorn to ControllerAction.LightHorn,
            binding.actionWipers to ControllerAction.Wipers,
            binding.actionBeacon to ControllerAction.Beacon,
            binding.actionDiffLock to ControllerAction.DiffLock,
            binding.actionLiftAxle to ControllerAction.LiftAxle,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sensorManager = checkNotNull(ContextCompat.getSystemService(this, SensorManager::class.java))
        tiltSensor = obtainTiltSensor()

        val lockDistancePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            THROTTLE_LOCK_DISTANCE_DP,
            resources.displayMetrics,
        )
        brakePedal = PedalHandler(this, lockDistancePx)
        gasPedal = PedalHandler(this, lockDistancePx)
        cruiseGestureDetector = GestureDetector(this, CruiseGestureListener())

        setUpViews()
        enterFullscreen()
        startClient(useSpecifiedServer = settings.useSpecifiedServer)

        if (!settings.guideShown) {
            settings.guideShown = true
            settings.lastShownReleaseNotes = resources.getInteger(R.integer.version)
            startActivity(Intent(this, GuideActivity::class.java))
        } else if (settings.lastShownReleaseNotes != resources.getInteger(R.integer.version)) {
            showReleaseNotesDialog()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setUpViews() = with(binding) {
        breakLevelView.fillColor = ContextCompat.getColor(this@MainActivity, R.color.indicatorRed)
        gasLevelView.fillColor = ContextCompat.getColor(this@MainActivity, R.color.indicatorGreen)

        breakLayout.setOnTouchListener { view, event ->
            // Braking always releases the locked throttle
            if (event.actionMasked == MotionEvent.ACTION_DOWN) gasPedal.unlock()
            handlePedalTouch(brakePedal, view, event)
            false
        }
        gasLayout.setOnTouchListener { view, event ->
            handlePedalTouch(gasPedal, view, event)
            cruiseGestureDetector.onTouchEvent(event)
            false
        }
        buttonHorn.setOnTouchListener { _, event -> onHornTouch(event) }

        connectionIndicator.setOnClickListener { showSignalStrength() }
        pauseButton.setOnClickListener { togglePause() }
        settingsButton.setOnClickListener { showMenu() }
        buttonLeftSignal.setOnClickListener {
            ifControllable { client.updateState { it.copy(leftSignalClick = !it.leftSignalClick) } }
        }
        buttonRightSignal.setOnClickListener {
            ifControllable { client.updateState { it.copy(rightSignalClick = !it.rightSignalClick) } }
        }
        buttonAllSignals.setOnClickListener {
            ifControllable { client.updateState { it.copy(emergencyClick = !it.emergencyClick) } }
        }
        buttonParking.setOnClickListener {
            ifControllable { client.updateState { it.copy(parkingBrakeClick = !it.parkingBrakeClick) } }
        }
        buttonLights.setOnClickListener {
            ifControllable { client.updateState { it.copy(lightsClick = !it.lightsClick) } }
        }

        actionsButton.setOnClickListener { actionsPanel.isVisible = !actionsPanel.isVisible }
        actionViews.forEach { (view, action) ->
            view.setOnClickListener {
                ifControllable {
                    client.clickAction(action)
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        calibrationOffset = settings.calibrationOffset
        steeringCurve = SteeringCurve(settings.steeringDeadZone, settings.steeringMaxAngle, settings.steeringExponent)
        useForceFeedback = settings.forceFeedback
        analogPedalsMode = settings.pedalMode == PedalMode.Analog
        brakePedal.configure(analogPedalsMode, lockAllowed = false)
        gasPedal.configure(analogPedalsMode, lockAllowed = settings.throttleLock)
        releasePedals()
        client.resume()
        wifiLock.acquire()
        if (isConnected) registerTiltSensor()
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        // Locked throttle mustn't come back after returning to the app
        releasePedals()
        client.pause()
        wifiLock.release()
    }

    override fun onDestroy() {
        super.onDestroy()
        client.stop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterFullscreen()
    }

    /*
     * Gravity sensor isn't affected by shaking and touches of the screen (e.g. pressing pedals).
     * Without a gyroscope it is only a filtered accelerometer with a delay, so the accelerometer is used then.
     */
    private fun obtainTiltSensor(): Sensor? {
        val hasGyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
        val gravity = if (hasGyroscope) sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY) else null
        return gravity ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    // The state is sent 50 times per second, the game rate is enough
    private fun registerTiltSensor() {
        tiltSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    private fun startClient(useSpecifiedServer: Boolean) {
        binding.pauseButton.setImageResource(R.drawable.pause_btn_resumed)
        if (!isWifiEnabled()) Toaster.show(R.string.no_wifi_conn_detected)

        searchingByBroadcast = !useSpecifiedServer
        if (!useSpecifiedServer) {
            Toaster.show(R.string.searching_on_local)
            client.start(null, settings.serverPort)
            return
        }

        val serverIp = settings.specifiedServerIp
        if (isValidIpv4(serverIp)) {
            Toaster.show(R.string.trying_to_connect)
            client.start(serverIp, settings.serverPort)
        } else {
            Toaster.show(R.string.def_server_ip_not_correct)
        }
    }

    private inline fun ifControllable(block: () -> Unit) {
        if (isConnected && !client.isPausedByUser) block()
    }

    private fun togglePause() {
        if (!isConnected) return
        if (client.isPausedByUser) {
            client.resumeByUser()
            binding.pauseButton.setImageResource(R.drawable.pause_btn_resumed)
        } else {
            releasePedals()
            client.pauseByUser()
            binding.pauseButton.setImageResource(R.drawable.pause_btn_paused)
            app.ads.tryShowFullscreenAd(this)
        }
    }

    /* Pedals */

    private fun handlePedalTouch(pedal: PedalHandler, view: View, event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pedal.onDown(event.x, event.y, view.height)
                warnIfAnalogUnavailable()
            }

            MotionEvent.ACTION_MOVE -> pedal.onMove(event.x, event.y)

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> pedal.onUp()
        }
    }

    private fun warnIfAnalogUnavailable() {
        if (analogPedalsMode && isConnected && !analogUnavailableWarned && !client.isAnalogPedalsAvailable) {
            analogUnavailableWarned = true
            Toaster.show(R.string.analog_pedals_unavailable)
        }
    }

    private fun releasePedals() {
        brakePedal.release()
        gasPedal.release()
        sendPedalsState()
        updatePedalViews()
    }

    // Analog levels are used when the server supports them, keys otherwise
    private fun sendPedalsState() {
        val analog = analogPedalsMode && client.isAnalogPedalsAvailable
        val brakeLevel = brakePedal.level
        val gasLevel = gasPedal.level
        client.updateState {
            it.copy(
                brakePressed = !analog && brakeLevel > 0,
                gasPressed = !analog && gasLevel > 0,
                brakeLevel = if (analog) brakeLevel else 0f,
                gasLevel = if (analog) gasLevel else 0f,
            )
        }
    }

    private fun updatePedalViews() = with(binding) {
        breakLevelView.visibility = if (analogPedalsMode) View.VISIBLE else View.INVISIBLE
        breakLevelView.setState(brakePedal.level, locked = false)

        val gasLocked = gasPedal.isLocked
        gasLevelView.visibility = if (analogPedalsMode || gasLocked) View.VISIBLE else View.INVISIBLE
        gasLevelView.setState(gasPedal.level, gasLocked)

        gasLockLabel.isVisible = gasLocked
        if (gasLocked) gasLockLabel.text = getString(R.string.gas_locked, (gasPedal.level * 100).roundToInt())
    }

    override fun onPedalChanged(pedal: PedalHandler) {
        sendPedalsState()
        updatePedalViews()
    }

    override fun onPedalLockChanged(pedal: PedalHandler, locked: Boolean) {
        binding.gasLayout.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        updatePedalViews()
    }

    private fun onHornTouch(event: MotionEvent): Boolean {
        if (!isConnected) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                binding.buttonHorn.startCachedAnimation(R.anim.button_upscale)
                val horn = if (settings.pneumaticHorn) HornState.Pneumatic else HornState.Horn
                client.updateState { it.copy(horn = horn) }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                binding.buttonHorn.startCachedAnimation(R.anim.button_upscale_reverse)
                client.updateState { it.copy(horn = HornState.Off) }
            }
        }
        return false
    }

    private inner class CruiseGestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (e1 == null || !isConnected || client.isPausedByUser) return false
            val movedX = abs(e1.x - e2.x)
            val movedY = abs(e1.y - e2.y)
            val isFastVerticalSwipeUp = velocityY < 0 &&
                abs(velocityY) / 1000 > CRUISE_MIN_VELOCITY &&
                movedX / movedY < CRUISE_MAX_SIDEWAYS_RATIO
            if (isFastVerticalSwipeUp) {
                client.updateState { it.copy(cruiseClick = !it.cruiseClick) }
                binding.gasImage.startCachedAnimation(R.anim.gas_cruise)
            }
            return false
        }
    }

    /* Tilt sensor */

    override fun onSensorChanged(event: SensorEvent) {
        lastRawTiltY = event.values[1]
        val y = lastRawTiltY + calibrationOffset
        val steering = steeringCurve.apply(if (isReverseLandscape) -y else y)
        client.updateState { it.copy(steering = steering) }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit

    /* Server events (network thread) */

    override fun onConnectionChanged(connected: Boolean) = runOnUiThread {
        isConnected = connected
        if (connected) {
            analogUnavailableWarned = false
            binding.connectionIndicator.setImageResource(R.drawable.connection_indicator_green)
            Toaster.show("${getString(R.string.connected_to_server_at)} ${client.serverAddress}")
            registerTiltSensor()
            reportConnected()
        } else {
            binding.connectionIndicator.setImageResource(R.drawable.connection_indicator_red)
            Toaster.show(R.string.connection_lost)
            sensorManager.unregisterListener(this)
            releasePedals()
            shownServerState = null
            showActionStates(engineOn = false, trailerAttached = false, wipersOn = false, beaconOn = false)
        }
    }

    private fun reportConnected() = Analytics.report(
        Analytics.EVENT_SERVER_CONNECTED,
        mapOf(
            "search" to if (searchingByBroadcast) "broadcast" else "ip",
            "tilt_sensor" to if (tiltSensor?.type == Sensor.TYPE_GRAVITY) "gravity" else "accelerometer",
            "pedals" to if (analogPedalsMode) "analog" else "digital",
        ),
    )

    override fun onServerState(state: ServerState) {
        if (useForceFeedback && state.ffbDurationMs > 0) vibrate(state.ffbDurationMs)

        // Only changes are shown, the server sends its state 50 times per second
        val withoutFfb = state.copy(ffbDurationMs = 0)
        if (withoutFfb == shownServerState) return
        val previous = shownServerState
        shownServerState = withoutFfb
        runOnUiThread { showServerState(previous, withoutFfb) }
    }

    private fun showServerState(previous: ServerState?, state: ServerState) = with(binding) {
        if (previous?.parkingBrake != state.parkingBrake) {
            buttonParking.setImageResource(
                if (state.parkingBrake) R.drawable.parking_break_on else R.drawable.parking_break_off,
            )
            buttonParking.startCachedAnimation(
                if (state.parkingBrake) R.anim.button_upscale else R.anim.button_upscale_reverse,
            )
        }

        if (previous?.lightsMode != state.lightsMode) showLights(state.lightsMode)

        if (previous == null ||
            previous.leftBlinker != state.leftBlinker ||
            previous.rightBlinker != state.rightBlinker
        ) {
            showBlinkers(previous, state)
        }

        showActionStates(state.engineOn, state.trailerAttached, state.wipersOn, state.beaconOn)

        // Pedals state should be sent in the right form (axes or keys)
        if (previous?.analogPedalsAvailable != state.analogPedalsAvailable) sendPedalsState()
    }

    private fun showLights(mode: Int) = with(binding.buttonLights) {
        when (mode) {
            LIGHTS_OFF -> {
                setImageResource(R.drawable.lights_off)
                startCachedAnimation(R.anim.button_upscale_reverse)
            }

            LIGHTS_PARKING -> {
                setImageResource(R.drawable.lights_gab)
                startCachedAnimation(R.anim.button_upscale)
            }

            LIGHTS_LOW_BEAM -> setImageResource(R.drawable.lights_low)

            else -> setImageResource(R.drawable.lights_high)
        }
    }

    private fun showBlinkers(previous: ServerState?, state: ServerState) = with(binding) {
        buttonLeftSignal.setImageResource(if (state.leftBlinker) R.drawable.left_enabled else R.drawable.left_disabled)
        buttonRightSignal.setImageResource(
            if (state.rightBlinker) R.drawable.right_enabled else R.drawable.right_disabled,
        )
        val emergencyOn = state.leftBlinker && state.rightBlinker
        val wasEmergencyOn = previous != null && previous.leftBlinker && previous.rightBlinker
        if (emergencyOn != wasEmergencyOn) {
            buttonAllSignals.setImageResource(if (emergencyOn) R.drawable.emergency_on else R.drawable.emergency_off)
            buttonAllSignals.startCachedAnimation(
                if (emergencyOn) R.anim.button_upscale_soft else R.anim.button_upscale_soft_reverse,
            )
        }
    }

    private fun showActionStates(engineOn: Boolean, trailerAttached: Boolean, wipersOn: Boolean, beaconOn: Boolean) {
        binding.actionEngine.isActivated = engineOn
        binding.actionTrailer.isActivated = trailerAttached
        binding.actionWipers.isActivated = wipersOn
        binding.actionBeacon.isActivated = beaconOn
    }

    /* Menu and dialogs */

    private fun showMenu() {
        if (supportFragmentManager.findFragmentByTag(MenuDialogFragment.TAG) == null) {
            MenuDialogFragment().show(supportFragmentManager, MenuDialogFragment.TAG)
        }
    }

    override fun onMenuItemSelected(item: MenuDialogFragment.Item) {
        when (item) {
            MenuDialogFragment.Item.AutoConnect -> startClient(useSpecifiedServer = false)

            MenuDialogFragment.Item.DefaultConnect -> startClient(useSpecifiedServer = true)

            MenuDialogFragment.Item.Disconnect -> {
                binding.pauseButton.setImageResource(R.drawable.pause_btn_resumed)
                client.stop()
            }

            MenuDialogFragment.Item.Guide -> startActivity(Intent(this, GuideActivity::class.java))

            MenuDialogFragment.Item.Calibration -> showCalibrationDialog()

            MenuDialogFragment.Item.Settings -> startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun showReleaseNotesDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.version_changes_title)
            .setMessage(R.string.version_changes_text)
            .setPositiveButton(R.string.close) { _, _ ->
                settings.lastShownReleaseNotes = resources.getInteger(R.integer.version)
            }
            .setCancelable(false)
            .create()
            .showKeepingFullscreen()
    }

    private fun showCalibrationDialog() {
        AlertDialog.Builder(this)
            .setItems(R.array.calibration_items) { _, which ->
                if (which == 0) {
                    calibrationOffset = -lastRawTiltY
                    Toaster.show(R.string.calibration_completed)
                } else {
                    calibrationOffset = 0f
                    Toaster.show(R.string.calibration_reset)
                }
                settings.calibrationOffset = calibrationOffset
            }
            .create()
            .showKeepingFullscreen()
    }

    private fun showSignalStrength() {
        if (isWifiEnabled()) {
            Toaster.show("${getString(R.string.signal_strength)} ${wifiRssi()} dBm")
        } else {
            Toaster.show(R.string.no_wifi_conn_detected)
        }
    }

    /* System services */

    private fun wifiManager() = ContextCompat.getSystemService(applicationContext, WifiManager::class.java)

    private fun isWifiEnabled() = wifiManager()?.isWifiEnabled == true

    @Suppress("DEPRECATION")
    private fun wifiRssi() = wifiManager()?.connectionInfo?.rssi ?: 0

    private fun obtainVibrator(): Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        checkNotNull(ContextCompat.getSystemService(this, VibratorManager::class.java)).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        checkNotNull(ContextCompat.getSystemService(this, Vibrator::class.java))
    }

    private fun vibrate(durationMs: Long) {
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs)
        }
    }

    private fun View.startCachedAnimation(@AnimRes resId: Int) {
        startAnimation(animations.getOrPut(id to resId) { AnimationUtils.loadAnimation(this@MainActivity, resId) })
    }

    private companion object {
        // Horizontal swipe distance on gas which locks the throttle
        const val THROTTLE_LOCK_DISTANCE_DP = 70f
        const val CRUISE_MIN_VELOCITY = 1.5f
        const val CRUISE_MAX_SIDEWAYS_RATIO = 0.5f

        const val LIGHTS_OFF = 0
        const val LIGHTS_PARKING = 1
        const val LIGHTS_LOW_BEAM = 2
    }
}

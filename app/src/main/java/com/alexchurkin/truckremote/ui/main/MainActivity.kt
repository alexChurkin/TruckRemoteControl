package com.alexchurkin.truckremote.ui.main

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.activity.viewModels
import androidx.annotation.AnimRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.alexchurkin.truckremote.R
import com.alexchurkin.truckremote.app
import com.alexchurkin.truckremote.data.controller.ConnectionState
import com.alexchurkin.truckremote.data.controller.ControllerAction
import com.alexchurkin.truckremote.data.controller.ServerState
import com.alexchurkin.truckremote.databinding.ActivityMainBinding
import com.alexchurkin.truckremote.ui.guide.GuideActivity
import com.alexchurkin.truckremote.ui.settings.SettingsActivity
import com.alexchurkin.truckremote.ui.widget.PedalHinge
import com.alexchurkin.truckremote.ui.widget.showPedalPress
import com.alexchurkin.truckremote.util.Toaster
import com.alexchurkin.truckremote.util.enterFullscreen
import com.alexchurkin.truckremote.util.showKeepingFullscreen
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Controller screen: renders [MainUiState] and passes touches to [MainViewModel].
 * The logic (connection, steering, pedals) is in the view model.
 */
class MainActivity :
    AppCompatActivity(),
    MenuDialogFragment.Listener {

    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory }
    private lateinit var binding: ActivityMainBinding
    private lateinit var cruiseGestureDetector: GestureDetector

    // The truck state shown now: only changes are animated
    private var shownTruck: ServerState? = null

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

        viewModel.setThrottleLockDistance(
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, THROTTLE_LOCK_DISTANCE_DP, resources.displayMetrics),
        )
        cruiseGestureDetector = GestureDetector(this, CruiseGestureListener())
        setUpViews()
        enterFullscreen()
        observeViewModel()

        if (savedInstanceState == null) {
            when (viewModel.start(releaseNotesVersion())) {
                StartAction.Guide -> startActivity(Intent(this, GuideActivity::class.java))
                StartAction.ReleaseNotes -> showReleaseNotesDialog()
                StartAction.None -> Unit
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.setForeground(true)
    }

    override fun onPause() {
        super.onPause()
        viewModel.setForeground(false)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterFullscreen()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setUpViews() = with(binding) {
        breakLevelView.fillColor = ContextCompat.getColor(this@MainActivity, R.color.indicatorRed)
        gasLevelView.fillColor = ContextCompat.getColor(this@MainActivity, R.color.indicatorGreen)

        breakLayout.setOnTouchListener { view, event ->
            onPedalTouch(Pedal.Brake, view, event)
            false
        }
        gasLayout.setOnTouchListener { view, event ->
            onPedalTouch(Pedal.Gas, view, event)
            cruiseGestureDetector.onTouchEvent(event)
            false
        }
        buttonHorn.setOnTouchListener { _, event -> onHornTouch(event) }

        connectionIndicator.setOnClickListener { showSignalInfo() }
        pauseButton.setOnClickListener { viewModel.togglePause() }
        settingsButton.setOnClickListener { showMenu() }
        buttonLeftSignal.setOnClickListener { viewModel.onLeftSignal() }
        buttonRightSignal.setOnClickListener { viewModel.onRightSignal() }
        buttonAllSignals.setOnClickListener { viewModel.onEmergencySignal() }
        buttonParking.setOnClickListener { viewModel.onParkingBrake() }
        buttonLights.setOnClickListener { viewModel.onLights() }

        actionsButton.setOnClickListener { actionsPanel.isVisible = !actionsPanel.isVisible }
        actionViews.forEach { (view, action) ->
            view.setOnClickListener {
                if (viewModel.onAction(action)) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect(::render) }
                launch { viewModel.effects.collect(::showEffect) }
            }
        }
    }

    /* Rendering */

    private fun render(state: MainUiState) {
        showConnectionIndicator(state)
        binding.pauseButton.setImageResource(
            if (state.pausedByUser) R.drawable.pause_btn_paused else R.drawable.pause_btn_resumed,
        )
        showPedals(state.pedals)
        // The truck state is unknown without the server: nothing is shown as turned on
        showTruck(state.truck ?: UNKNOWN_TRUCK)
    }

    // Green: good link, yellow: bad link or resuming, red: no connection
    private fun showConnectionIndicator(state: MainUiState) {
        val quality = state.linkQuality
        binding.connectionIndicator.setImageResource(
            when {
                state.connection == ConnectionState.Resuming -> R.drawable.connection_indicator_yellow
                state.connection != ConnectionState.Connected -> R.drawable.connection_indicator_red
                quality != null && !quality.isGood -> R.drawable.connection_indicator_yellow
                else -> R.drawable.connection_indicator_green
            },
        )
    }

    private fun showPedals(pedals: PedalsUiState) = with(binding) {
        breakLevelView.visibility = if (pedals.analog) View.VISIBLE else View.INVISIBLE
        breakLevelView.setState(pedals.brakeLevel, locked = false)

        gasLevelView.visibility = if (pedals.analog || pedals.gasLocked) View.VISIBLE else View.INVISIBLE
        gasLevelView.setState(pedals.gasLevel, pedals.gasLocked)

        gasLockLabel.isVisible = pedals.gasLocked
        if (pedals.gasLocked) {
            gasLockLabel.text = getString(R.string.gas_locked, (pedals.gasLevel * PERCENT).roundToInt())
        }

        // An analog pedal follows the finger, a digital one is pressed down smoothly
        breakImage.showPedalPress(pedals.brakeLevel, PedalHinge.Top, animated = !pedals.analog)
        gasImage.showPedalPress(pedals.gasLevel, PedalHinge.Bottom, animated = !pedals.analog)
    }

    // The first state is shown without animations (e.g. after the screen is recreated)
    private fun showTruck(truck: ServerState) {
        val previous = shownTruck
        if (previous == truck) return
        shownTruck = truck
        showTruckChanges(previous, truck)
    }

    private fun showTruckChanges(previous: ServerState?, truck: ServerState) = with(binding) {
        if (previous?.parkingBrake != truck.parkingBrake) {
            buttonParking.setImageResource(
                if (truck.parkingBrake) R.drawable.parking_break_on else R.drawable.parking_break_off,
            )
            if (previous != null) {
                buttonParking.startCachedAnimation(
                    if (truck.parkingBrake) R.anim.button_upscale else R.anim.button_upscale_reverse,
                )
            }
        }

        if (previous?.lightsMode != truck.lightsMode) showLights(truck.lightsMode, animate = previous != null)

        if (previous == null ||
            previous.leftBlinker != truck.leftBlinker ||
            previous.rightBlinker != truck.rightBlinker
        ) {
            showBlinkers(previous, truck)
        }

        actionEngine.isActivated = truck.engineOn
        actionTrailer.isActivated = truck.trailerAttached
        actionWipers.isActivated = truck.wipersOn
        actionBeacon.isActivated = truck.beaconOn
    }

    private fun showLights(mode: Int, animate: Boolean) = with(binding.buttonLights) {
        when (mode) {
            LIGHTS_OFF -> {
                setImageResource(R.drawable.lights_off)
                if (animate) startCachedAnimation(R.anim.button_upscale_reverse)
            }

            LIGHTS_PARKING -> {
                setImageResource(R.drawable.lights_gab)
                if (animate) startCachedAnimation(R.anim.button_upscale)
            }

            LIGHTS_LOW_BEAM -> setImageResource(R.drawable.lights_low)

            else -> setImageResource(R.drawable.lights_high)
        }
    }

    private fun showBlinkers(previous: ServerState?, truck: ServerState) = with(binding) {
        buttonLeftSignal.setImageResource(if (truck.leftBlinker) R.drawable.left_enabled else R.drawable.left_disabled)
        buttonRightSignal.setImageResource(
            if (truck.rightBlinker) R.drawable.right_enabled else R.drawable.right_disabled,
        )
        val emergencyOn = truck.leftBlinker && truck.rightBlinker
        val wasEmergencyOn = previous != null && previous.leftBlinker && previous.rightBlinker
        if (previous == null || emergencyOn != wasEmergencyOn) {
            buttonAllSignals.setImageResource(if (emergencyOn) R.drawable.emergency_on else R.drawable.emergency_off)
            if (previous != null) {
                buttonAllSignals.startCachedAnimation(
                    if (emergencyOn) R.anim.button_upscale_soft else R.anim.button_upscale_soft_reverse,
                )
            }
        }
    }

    /* Effects */

    private fun showEffect(effect: MainEffect) {
        when (effect) {
            is MainEffect.Message -> Toaster.show(
                if (effect.suffix == null) getString(effect.text) else "${getString(effect.text)} ${effect.suffix}",
            )

            MainEffect.ServerNotFound -> showServerNotFoundHint()

            MainEffect.ShowAd -> app.container.ads.tryShowFullscreenAd(this)

            MainEffect.ThrottleLockChanged -> binding.gasLayout.performHapticFeedback(
                HapticFeedbackConstants.LONG_PRESS,
            )
        }
    }

    private fun showServerNotFoundHint() {
        if (isFinishing) return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.server_not_found_title)
            .setMessage(R.string.server_not_found_text)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(R.string.settings) { _, _ -> startActivity(Intent(this, SettingsActivity::class.java)) }
            .create()
            .showKeepingFullscreen()
    }

    private fun showSignalInfo() {
        val info = viewModel.signalInfo()
        if (!info.wifiEnabled) {
            Toaster.show(R.string.no_wifi_conn_detected)
            return
        }
        val signal = "${getString(R.string.signal_strength)} ${info.rssi} dBm"
        val quality = info.linkQuality
        if (quality == null) {
            Toaster.show(signal)
        } else {
            val loss = quality.lossPercent?.let { "$it%" } ?: "—"
            Toaster.show("$signal\n${getString(R.string.link_quality, loss, quality.jitterMs)}")
        }
    }

    /* Touches */

    private fun onPedalTouch(pedal: Pedal, view: View, event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> viewModel.onPedalDown(pedal, event.x, event.y, view.height)
            MotionEvent.ACTION_MOVE -> viewModel.onPedalMove(pedal, event.x, event.y)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> viewModel.onPedalUp(pedal)
        }
    }

    private fun onHornTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN ->
                if (viewModel.onHorn(pressed = true)) binding.buttonHorn.startCachedAnimation(R.anim.button_upscale)

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                if (viewModel.onHorn(pressed = false)) {
                    binding.buttonHorn.startCachedAnimation(R.anim.button_upscale_reverse)
                }
        }
        return false
    }

    private inner class CruiseGestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (e1 == null) return false
            val movedX = abs(e1.x - e2.x)
            val movedY = abs(e1.y - e2.y)
            val isFastVerticalSwipeUp = velocityY < 0 &&
                abs(velocityY) / MS_IN_SECOND > CRUISE_MIN_VELOCITY &&
                movedX / movedY < CRUISE_MAX_SIDEWAYS_RATIO
            if (isFastVerticalSwipeUp && viewModel.onCruiseSwipe()) {
                binding.gasImage.startCachedAnimation(R.anim.gas_cruise)
            }
            return false
        }
    }

    /* Menu and dialogs */

    private fun showMenu() {
        if (supportFragmentManager.findFragmentByTag(MenuDialogFragment.TAG) == null) {
            MenuDialogFragment().show(supportFragmentManager, MenuDialogFragment.TAG)
        }
    }

    override fun onMenuItemSelected(item: MenuDialogFragment.Item) {
        when (item) {
            MenuDialogFragment.Item.AutoConnect -> viewModel.connect(useSpecifiedServer = false)
            MenuDialogFragment.Item.DefaultConnect -> viewModel.connect(useSpecifiedServer = true)
            MenuDialogFragment.Item.Disconnect -> viewModel.disconnect()
            MenuDialogFragment.Item.Guide -> startActivity(Intent(this, GuideActivity::class.java))
            MenuDialogFragment.Item.Calibration -> showCalibrationDialog()
            MenuDialogFragment.Item.Settings -> startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun releaseNotesVersion() = resources.getInteger(R.integer.version)

    private fun showReleaseNotesDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.version_changes_title)
            .setMessage(R.string.version_changes_text)
            .setPositiveButton(R.string.close) { _, _ -> viewModel.onReleaseNotesShown(releaseNotesVersion()) }
            .setCancelable(false)
            .create()
            .showKeepingFullscreen()
    }

    private fun showCalibrationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.calibration)
            .setItems(R.array.calibration_items) { _, which ->
                if (which == 0) viewModel.calibrate() else viewModel.resetCalibration()
            }
            .create()
            .showKeepingFullscreen()
    }

    private fun View.startCachedAnimation(@AnimRes resId: Int) {
        startAnimation(animations.getOrPut(id to resId) { AnimationUtils.loadAnimation(this@MainActivity, resId) })
    }

    private companion object {
        val UNKNOWN_TRUCK = ServerState(
            engineOn = false,
            parkingBrake = false,
            leftBlinker = false,
            rightBlinker = false,
            lightsMode = 0,
            ffbDurationMs = 0,
        )

        // Horizontal swipe distance on gas which locks the throttle
        const val THROTTLE_LOCK_DISTANCE_DP = 70f

        // Pixels per millisecond
        const val CRUISE_MIN_VELOCITY = 1.5f
        const val CRUISE_MAX_SIDEWAYS_RATIO = 0.5f
        const val MS_IN_SECOND = 1000
        const val PERCENT = 100

        const val LIGHTS_OFF = 0
        const val LIGHTS_PARKING = 1
        const val LIGHTS_LOW_BEAM = 2
    }
}

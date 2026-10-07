package com.alexchurkin.truckremote.ui.main

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.activity.viewModels
import androidx.annotation.AnimRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.alexchurkin.truckremote.R
import com.alexchurkin.truckremote.app
import com.alexchurkin.truckremote.data.controller.ConnectionState
import com.alexchurkin.truckremote.data.controller.ControllerAction
import com.alexchurkin.truckremote.data.controller.ServerState
import com.alexchurkin.truckremote.data.settings.AppMode
import com.alexchurkin.truckremote.databinding.ActivityMainBinding
import com.alexchurkin.truckremote.ui.dashboard.DashboardActivity
import com.alexchurkin.truckremote.ui.guide.GuideActivity
import com.alexchurkin.truckremote.ui.mode.ModeActivity
import com.alexchurkin.truckremote.ui.settings.SettingsActivity
import com.alexchurkin.truckremote.ui.widget.PedalHinge
import com.alexchurkin.truckremote.ui.widget.showPedalPress
import com.alexchurkin.truckremote.util.enterFullscreen
import com.alexchurkin.truckremote.util.showKeepingFullscreen
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.play.core.review.ReviewManagerFactory
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

    // The gas lock shown now: its change is animated
    private var shownGasLocked: Boolean? = null

    // Every view has its own animation instances: one instance can't run on several views
    private val animations = HashMap<Pair<Int, Int>, Animation>()

    // Actions that are on in the game: their buttons are highlighted
    private var activeActions by mutableStateOf(emptySet<ControllerAction>())
    private var actionBadges by mutableStateOf(emptyMap<ControllerAction, String>())

    // Actions the player has no key for in the game: their buttons are marked
    private var unboundActions by mutableStateOf(emptySet<ControllerAction>())

    // The screen is dimmed a moment after the controls are paused (to save the battery), a touch brightens it
    private var paused = false
    private var dimJob: Job? = null

    private var messageJob: Job? = null

    // The quick actions panel is open; the instruments (Compose) give their place to it
    private var actionsShown by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app starts here: a device that is only a dashboard goes to it, a new user is asked first
        val other = when (app.container.settings.appMode) {
            AppMode.Controller -> null
            AppMode.Dashboard -> DashboardActivity::class.java
            null -> ModeActivity::class.java
        }
        if (other != null) {
            startActivity(Intent(this, other))
            finish()
            return
        }
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel.setThrottleLockDistance(
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, THROTTLE_LOCK_DISTANCE_DP, resources.displayMetrics),
        )
        cruiseGestureDetector = GestureDetector(this, CruiseGestureListener())
        setUpViews()
        if (savedInstanceState?.getBoolean(STATE_ACTIONS_SHOWN) == true) showActionsPanel(true, animate = false)
        enterFullscreen()
        observeViewModel()

        // Also for a screen restored after the app was killed in background: its new view model must connect
        when (viewModel.start(releaseNotesVersion())) {
            StartAction.Guide -> startActivity(Intent(this, GuideActivity::class.java))
            StartAction.ReleaseNotes -> showReleaseNotesDialog()
            StartAction.Review -> requestReview()
            StartAction.None -> Unit
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

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (paused && event.actionMasked == MotionEvent.ACTION_DOWN) scheduleDimming()
        return super.dispatchTouchEvent(event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterFullscreen()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_ACTIONS_SHOWN, actionsShown)
    }

    /*
     * The quick actions panel takes the middle of the screen: everything there but the instruments fades out
     * (the blinkers, the horn, the parking brake, the lights and the cruise control). The hidden buttons keep
     * their places under the instruments, but nothing of them is seen or touched until the panel is closed.
     */
    private fun showActionsPanel(show: Boolean, animate: Boolean) = with(binding) {
        actionsShown = show
        val shift = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, ACTIONS_SHIFT_DP, resources.displayMetrics)
        actionsPanel.fade(show, offset = shift, hiddenVisibility = View.GONE, animate = animate)
        val controls =
            listOf(buttonLeftSignal, buttonAllSignals, buttonRightSignal, buttonHorn, buttonParking, buttonLights)
        controls.forEach {
            it.fade(!show, offset = -shift, hiddenVisibility = View.INVISIBLE, animate = animate)
        }
    }

    // Fades in from [offset] or out to it; a hidden view doesn't take touches
    private fun View.fade(show: Boolean, offset: Float, hiddenVisibility: Int, animate: Boolean) {
        animate().cancel()
        if (!animate) {
            alpha = if (show) 1f else 0f
            translationY = if (show) 0f else offset
            visibility = if (show) View.VISIBLE else hiddenVisibility
        } else if (show) {
            if (visibility != View.VISIBLE) {
                alpha = 0f
                translationY = offset
                visibility = View.VISIBLE
            }
            animate().alpha(1f).translationY(0f).setDuration(ACTIONS_ANIMATION_MS).withEndAction(null).start()
        } else {
            animate().alpha(0f).translationY(offset).setDuration(ACTIONS_ANIMATION_MS)
                .withEndAction { visibility = hiddenVisibility }
                .start()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setUpViews() = with(binding) {
        breakImage.fillColor = ContextCompat.getColor(this@MainActivity, R.color.pedalFillBrake)

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

        actionsButton.setOnClickListener { showActionsPanel(!actionsShown, animate = true) }
        dashboardView.setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            DashboardSlot(
                dashboard = state.truck?.dashboard.takeIf { state.showDashboard },
                job = state.truck?.job,
                imperialUnits = state.imperialUnits,
                speedingWarning = state.speedingWarning,
            )
        }
        cruiseView.setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            CruiseSlot(
                dashboard = state.truck?.dashboard.takeIf { state.showDashboard },
                visible = !actionsShown,
                imperialUnits = state.imperialUnits,
                onToggle = viewModel::onCruiseToggle,
                onStep = { up ->
                    viewModel.onAction(if (up) ControllerAction.CruiseUp else ControllerAction.CruiseDown)
                },
            )
        }
        actionsPanel.setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            ActionsPanel(
                layout = state.actionLayout,
                activeActions = activeActions,
                badges = actionBadges,
                unboundActions = unboundActions,
                onClick = viewModel::onAction,
                onHold = viewModel::onActionHold,
                onLayoutChange = viewModel::onActionLayoutChange,
                shown = actionsShown,
            )
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
        if (state.isPaused != paused) {
            paused = state.isPaused
            scheduleDimming()
        }
        showConnectionIndicator(state)
        binding.pauseButton.setImageResource(
            if (state.isPaused) R.drawable.pause_btn_paused else R.drawable.pause_btn_resumed,
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

    // The force of analog pedals (and of the locked gas) is shown inside them, with the percent beside them
    private fun showPedals(pedals: PedalsUiState) = with(binding) {
        val showBrake = pedals.analog
        val showGas = pedals.analog || pedals.gasLocked
        breakImage.level = if (showBrake) pedals.brakeLevel else 0f
        gasImage.level = if (showGas) pedals.gasLevel else 0f
        gasImage.fillColor = ContextCompat.getColor(
            this@MainActivity,
            if (pedals.gasLocked) R.color.pedalFillLocked else R.color.pedalFillGas,
        )
        showLevelLabel(breakLevelLabel, showBrake, pedals.brakeLevel)
        showLevelLabel(gasLevelLabel, showGas, pedals.gasLevel)
        showGasLock(pedals.gasLocked)

        // An analog pedal follows the finger, a digital one is pressed down smoothly
        breakImage.showPedalPress(pedals.brakeLevel, PedalHinge.Top, animated = !pedals.analog)
        gasImage.showPedalPress(pedals.gasLevel, PedalHinge.Bottom, animated = !pedals.analog)
    }

    private fun showLevelLabel(label: TextView, analog: Boolean, level: Float) {
        label.isInvisible = !analog || level <= 0f
        if (!label.isInvisible) label.text = getString(R.string.pedal_level, (level * PERCENT).roundToInt())
    }

    // The locked force is the same label: it moves aside, turns amber and a lock appears beside it
    private fun showGasLock(locked: Boolean) {
        val previous = shownGasLocked
        if (previous == locked) return
        shownGasLocked = locked

        val label = binding.gasLevelLabel
        val icon = binding.gasLockIcon
        label.setTextColor(ContextCompat.getColor(this, if (locked) R.color.lockedAmber else R.color.white))
        val shift = if (locked) {
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, LOCK_ICON_WIDTH_DP / 2, resources.displayMetrics)
        } else {
            0f
        }
        val iconAlpha = if (locked) 1f else 0f
        val iconScale = if (locked) 1f else LOCK_ICON_HIDDEN_SCALE
        label.animate().cancel()
        icon.animate().cancel()
        if (previous == null) {
            label.translationX = shift
            icon.translationX = shift
            icon.alpha = iconAlpha
            icon.scaleX = iconScale
            icon.scaleY = iconScale
        } else {
            label.animate().translationX(shift).setDuration(LOCK_ANIMATION_MS).start()
            icon.animate().translationX(shift).alpha(iconAlpha).scaleX(iconScale).scaleY(iconScale)
                .setDuration(LOCK_ANIMATION_MS).start()
        }
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

        showActions(truck)
    }

    // States of the quick actions panel buttons
    private fun showActions(truck: ServerState) {
        activeActions = buildSet {
            if (truck.engineOn) add(ControllerAction.Engine)
            if (truck.trailerAttached) add(ControllerAction.Trailer)
            if (truck.wipersOn) add(ControllerAction.Wipers)
            if (truck.beaconOn) add(ControllerAction.Beacon)
            if (truck.differentialLock) add(ControllerAction.DiffLock)
            if (truck.liftAxle) add(ControllerAction.LiftAxle)
            if (truck.engineBrake) add(ControllerAction.EngineBrake)
            if (truck.retarderLevel > 0) add(ControllerAction.RetarderUp)
        }
        // The retarder level on both of its buttons
        actionBadges = if (truck.retarderSteps > 0) {
            val level = "${truck.retarderLevel}/${truck.retarderSteps}"
            mapOf(ControllerAction.RetarderUp to level, ControllerAction.RetarderDown to level)
        } else {
            emptyMap()
        }
        unboundActions = truck.unboundActions
    }

    private fun scheduleDimming() {
        dimJob?.cancel()
        setDimmed(false)
        if (paused) {
            dimJob = lifecycleScope.launch {
                delay(DIM_DELAY_MS)
                setDimmed(true)
            }
        }
    }

    private fun setDimmed(dimmed: Boolean) {
        val brightness = if (dimmed) DIMMED_BRIGHTNESS else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        if (window.attributes.screenBrightness != brightness) {
            window.attributes = window.attributes.apply { screenBrightness = brightness }
        }
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
            is MainEffect.Message -> showMessage(
                if (effect.suffix == null) getString(effect.text) else "${getString(effect.text)} ${effect.suffix}",
            )

            MainEffect.ServerNotFound -> showServerNotFoundHint()

            MainEffect.ServerOutdated -> showServerOutdatedHint()

            MainEffect.ShowAd -> app.container.ads.tryShowFullscreenAd(this)

            is MainEffect.ActionUnbound -> showMessage(
                getString(R.string.action_unbound, getString(effect.action.button().label)),
            )

            MainEffect.ThrottleLockChanged -> binding.gasLayout.performHapticFeedback(
                HapticFeedbackConstants.LONG_PRESS,
            )
        }
    }

    // Only the latest message is shown, so quick events don't queue up
    private fun showMessage(text: String) {
        val view = binding.messageView
        messageJob?.cancel()
        view.text = text
        view.animate().alpha(1f).setDuration(MESSAGE_FADE_MS).start()
        messageJob = lifecycleScope.launch {
            delay(MESSAGE_MS)
            view.animate().alpha(0f).setDuration(MESSAGE_FADE_MS).start()
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

    private fun showServerOutdatedHint() {
        if (isFinishing) return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.server_outdated_title)
            .setMessage(R.string.server_outdated_text)
            .setPositiveButton(R.string.server_outdated_download) { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, getString(R.string.server_releases_link).toUri()))
            }
            .setNegativeButton(R.string.server_outdated_later, null)
            .create()
            .showKeepingFullscreen()
    }

    private fun showSignalInfo() {
        val info = viewModel.signalInfo()
        if (!info.wifiEnabled) {
            showMessage(getString(R.string.no_wifi_conn_detected))
            return
        }
        val signal = "${getString(R.string.signal_strength)} ${info.rssi} dBm"
        val quality = info.linkQuality
        if (quality == null) {
            showMessage(signal)
        } else {
            val loss = quality.lossPercent?.let { "$it%" } ?: "—"
            showMessage("$signal\n${getString(R.string.link_quality, loss, quality.jitterMs)}")
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

    // A double tap on the gas pedal turns the cruise control on and off
    private inner class CruiseGestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (viewModel.onCruiseToggle()) {
                binding.gasLayout.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
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

    // Google Play decides itself whether to show its dialog (and how often), the result isn't known to the app
    private fun requestReview() {
        val manager = ReviewManagerFactory.create(this)
        manager.requestReviewFlow().addOnCompleteListener { request ->
            if (request.isSuccessful && !isFinishing) manager.launchReviewFlow(this, request.result)
        }
    }

    private fun showReleaseNotesDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.version_changes_title)
            .setMessage(R.string.version_changes_text)
            .setPositiveButton(R.string.close) { _, _ -> viewModel.onReleaseNotesShown(releaseNotesVersion()) }
            .setCancelable(false)
            .create()
            .showKeepingFullscreen()
    }

    // The same as "Straighten the wheel" and "Reset straightening" of the settings, under the same names
    private fun showCalibrationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.calibrate_title)
            .setMessage(R.string.calibrate_dialog_text)
            .setPositiveButton(R.string.calibrate_action) { _, _ -> viewModel.calibrate() }
            .setNeutralButton(R.string.calibration_reset_title) { _, _ -> viewModel.resetCalibration() }
            .setNegativeButton(android.R.string.cancel, null)
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

        // Width of the lock in the layout: the press force and the lock stay centered over the pedal
        const val LOCK_ICON_WIDTH_DP = 28f
        const val LOCK_ICON_HIDDEN_SCALE = 0.5f
        const val LOCK_ANIMATION_MS = 180L

        const val MESSAGE_MS = 2500L
        const val MESSAGE_FADE_MS = 150L

        const val STATE_ACTIONS_SHOWN = "actionsShown"
        const val ACTIONS_ANIMATION_MS = 200L
        const val ACTIONS_SHIFT_DP = 16f

        const val DIM_DELAY_MS = 3000L
        const val DIMMED_BRIGHTNESS = 0.03f

        const val PERCENT = 100

        const val LIGHTS_OFF = 0
        const val LIGHTS_PARKING = 1
        const val LIGHTS_LOW_BEAM = 2
    }
}

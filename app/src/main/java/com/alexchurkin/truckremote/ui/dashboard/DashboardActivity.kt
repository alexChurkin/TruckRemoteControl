package com.alexchurkin.truckremote.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alexchurkin.truckremote.ui.settings.SettingsActivity
import com.alexchurkin.truckremote.ui.theme.TruckRemoteTheme
import com.alexchurkin.truckremote.util.enterFullscreen

/**
 * The dashboard mode: the instruments of the truck in full screen, for a tablet or a second phone beside the
 * controller. The screen stays on; the server is a viewer of it only while it is shown. The mode is left
 * in the settings.
 */
class DashboardActivity : AppCompatActivity() {

    private val viewModel: DashboardViewModel by viewModels { DashboardViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            TruckRemoteTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                LaunchedEffect(state.night) { setNightBrightness(state.night) }
                DashboardScreen(
                    state = state,
                    onOpenSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                    onNightChange = viewModel::setNight,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        enterFullscreen()
        viewModel.setForeground(true)
    }

    override fun onStop() {
        viewModel.setForeground(false)
        super.onStop()
    }

    // The lowest brightness at night, the system one by day
    private fun setNightBrightness(night: Boolean) {
        window.attributes = window.attributes.apply {
            screenBrightness = if (night) NIGHT_BRIGHTNESS else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    }

    private companion object {
        const val NIGHT_BRIGHTNESS = 0.01f
    }
}

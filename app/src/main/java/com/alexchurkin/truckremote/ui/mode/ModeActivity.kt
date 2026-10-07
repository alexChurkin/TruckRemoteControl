package com.alexchurkin.truckremote.ui.mode

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.alexchurkin.truckremote.app
import com.alexchurkin.truckremote.data.settings.AppMode
import com.alexchurkin.truckremote.ui.dashboard.DashboardActivity
import com.alexchurkin.truckremote.ui.main.MainActivity
import com.alexchurkin.truckremote.ui.shortcuts.AppShortcuts
import com.alexchurkin.truckremote.ui.theme.TruckRemoteTheme

/**
 * The first start: the user is asked what the device is for. The answer is saved (it can be changed in the settings)
 * and the app goes on in the chosen mode.
 */
class ModeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            TruckRemoteTheme { ModeScreen(onChoose = ::choose) }
        }
    }

    private fun choose(mode: AppMode) {
        app.container.settings.appMode = mode
        AppShortcuts.update(this, mode)
        startActivity(Intent(this, mode.screen()))
        finish()
    }
}

// The screen the app shows in the mode
fun AppMode.screen() = when (this) {
    AppMode.Controller -> MainActivity::class.java
    AppMode.Dashboard -> DashboardActivity::class.java
}

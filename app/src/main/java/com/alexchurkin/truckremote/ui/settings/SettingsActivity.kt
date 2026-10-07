package com.alexchurkin.truckremote.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alexchurkin.truckremote.R
import com.alexchurkin.truckremote.app
import com.alexchurkin.truckremote.data.settings.AppMode
import com.alexchurkin.truckremote.ui.guide.GuideActivity
import com.alexchurkin.truckremote.ui.mode.screen
import com.alexchurkin.truckremote.ui.shortcuts.AppShortcuts
import com.alexchurkin.truckremote.ui.theme.TruckRemoteTheme

// AppCompat applies the language chosen in the app on Android 12 and older
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Not on every rotation
        if (savedInstanceState == null) app.container.ads.tryShowFullscreenAd(this)

        setContent {
            TruckRemoteTheme {
                SettingsScreen(
                    viewModel = viewModel(factory = SettingsViewModel.Factory),
                    onBack = ::finish,
                    onOpenGuide = { startActivity(Intent(this, GuideActivity::class.java)) },
                    onOpenLink = ::openLink,
                    onAppModeChange = ::openMode,
                )
            }
        }
    }

    // The screens of the other mode are left behind
    private fun openMode(mode: AppMode) {
        AppShortcuts.update(this, mode)
        startActivity(
            Intent(this, mode.screen())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
    }

    private fun openLink(@StringRes link: Int) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, getString(link).toUri()))
        } catch (_: ActivityNotFoundException) {
            // No browser on the device
        }
    }
}

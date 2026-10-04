package com.alexchurkin.truckremote.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alexchurkin.truckremote.R
import com.alexchurkin.truckremote.app
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
                    onOpenGithub = ::openGithub,
                )
            }
        }
    }

    private fun openGithub() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, getString(R.string.github_link).toUri()))
        } catch (_: ActivityNotFoundException) {
            // No browser on the device
        }
    }
}

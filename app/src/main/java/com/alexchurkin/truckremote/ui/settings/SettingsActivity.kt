package com.alexchurkin.truckremote.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alexchurkin.truckremote.R
import com.alexchurkin.truckremote.app
import com.alexchurkin.truckremote.ui.theme.TruckRemoteTheme

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Not on every rotation
        if (savedInstanceState == null) app.ads.tryShowFullscreenAd(this)

        setContent {
            TruckRemoteTheme {
                SettingsScreen(
                    viewModel = viewModel(factory = SettingsViewModel.Factory),
                    onBack = ::finish,
                    onRestorePurchase = app.billing::restorePurchase,
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

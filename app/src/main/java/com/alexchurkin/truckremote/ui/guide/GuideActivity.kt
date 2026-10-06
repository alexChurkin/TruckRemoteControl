package com.alexchurkin.truckremote.ui.guide

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.alexchurkin.truckremote.ui.theme.TruckRemoteTheme

// The guide: how to start the server and what the controller screen does. Shown at the first start and from the menu
class GuideActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            TruckRemoteTheme { GuideScreen(onDone = ::finish) }
        }
    }
}

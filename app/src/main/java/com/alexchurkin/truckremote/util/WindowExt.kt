package com.alexchurkin.truckremote.util

import android.app.Activity
import android.app.Dialog
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding

fun Window.hideSystemBars() {
    WindowCompat.getInsetsController(this, decorView).apply {
        hide(WindowInsetsCompat.Type.systemBars())
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

fun Activity.enterFullscreen() = window.hideSystemBars()

/**
 * A dialog over a fullscreen activity: by default its window keeps away from the display cutout, which is
 * on one side in landscape, so the dialog isn't in the middle of the screen. The cutout area is used as well.
 */
fun Window.layoutInDisplayCutout() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
    attributes = attributes.apply {
        layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else {
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }
}

/**
 * Shows a dialog over a fullscreen activity without showing system bars:
 * the window is not focusable while it is being shown, so the bars stay hidden.
 */
fun Dialog.showKeepingFullscreen() {
    val window = window
    if (window == null) {
        show()
        return
    }
    window.setFlags(
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
    )
    window.layoutInDisplayCutout()
    show()
    window.hideSystemBars()
    window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
}

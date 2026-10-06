package com.alexchurkin.truckremote.ui.widget

import android.graphics.Color
import android.graphics.PorterDuff
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView

/**
 * Shows the pedal pressed to [level] (0..1) like a real pedal:
 * it tilts away around its hinge (with perspective) and gets a bit darker.
 * [animated]: the pedal goes to the level smoothly (digital press), otherwise it's shown at once (follows the finger).
 */
fun ImageView.showPedalPress(level: Float, hinge: PedalHinge, animated: Boolean) {
    pivotX = width / 2f
    pivotY = if (hinge == PedalHinge.Bottom) height.toFloat() else 0f
    cameraDistance = CAMERA_DISTANCE_DP * resources.displayMetrics.density

    // Positive rotation moves the top edge away, negative one moves the bottom edge away
    val sign = if (hinge == PedalHinge.Bottom) 1f else -1f
    val rotation = sign * MAX_ANGLE * level.coerceIn(0f, 1f)
    animate().cancel()
    if (animated && rotation != rotationX) {
        animate()
            .rotationX(rotation)
            .setDuration(ANIMATION_MS)
            .setInterpolator(DecelerateInterpolator())
            .setUpdateListener { showDarkening(sign * rotationX / MAX_ANGLE) }
            .start()
    } else {
        rotationX = rotation
        showDarkening(level)
    }
}

private fun ImageView.showDarkening(level: Float) {
    val press = level.coerceIn(0f, 1f)
    if (press > 0f) {
        val brightness = (MAX_CHANNEL * (1f - MAX_DARKENING * press)).toInt()
        setColorFilter(Color.rgb(brightness, brightness, brightness), PorterDuff.Mode.MULTIPLY)
    } else {
        clearColorFilter()
    }
}

private const val MAX_ANGLE = 32f
private const val MAX_DARKENING = 0.3f
private const val MAX_CHANNEL = 255
private const val ANIMATION_MS = 120L

// Bigger distance gives weaker perspective
private const val CAMERA_DISTANCE_DP = 1500f

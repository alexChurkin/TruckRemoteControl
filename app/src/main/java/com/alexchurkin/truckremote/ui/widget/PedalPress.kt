package com.alexchurkin.truckremote.ui.widget

import android.graphics.Color
import android.graphics.PorterDuff
import android.widget.ImageView

// Where the pedal is hinged: the opposite edge goes away from the driver when the pedal is pressed
enum class PedalHinge {
    // Floor-mounted pedal (gas): the top goes down
    Bottom,

    // Hanging pedal (brake): the bottom goes down
    Top,
}

/**
 * Shows the pedal pressed to [level] (0..1) like a real pedal:
 * it tilts away around its hinge (with perspective) and gets a bit darker.
 */
fun ImageView.showPedalPress(level: Float, hinge: PedalHinge) {
    pivotX = width / 2f
    pivotY = if (hinge == PedalHinge.Bottom) height.toFloat() else 0f
    cameraDistance = CAMERA_DISTANCE_DP * resources.displayMetrics.density

    // Positive rotation moves the top edge away, negative one moves the bottom edge away
    val angle = MAX_ANGLE * level.coerceIn(0f, 1f)
    animate()
        .rotationX(if (hinge == PedalHinge.Bottom) angle else -angle)
        .setDuration(ANIMATION_MS)
        .start()

    val brightness = (255 * (1f - MAX_DARKENING * level.coerceIn(0f, 1f))).toInt()
    if (level > 0f) {
        setColorFilter(Color.rgb(brightness, brightness, brightness), PorterDuff.Mode.MULTIPLY)
    } else {
        clearColorFilter()
    }
}

private const val MAX_ANGLE = 32f
private const val MAX_DARKENING = 0.3f
private const val ANIMATION_MS = 70L

// Bigger distance gives weaker perspective
private const val CAMERA_DISTANCE_DP = 1500f

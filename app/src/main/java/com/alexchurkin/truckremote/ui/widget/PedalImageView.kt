package com.alexchurkin.truckremote.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.AttributeSet
import androidx.annotation.ColorInt
import androidx.appcompat.widget.AppCompatImageView

/**
 * Pedal image that shows its press force: the pedal is filled with color from the bottom up to [level] (0..1).
 * The fill keeps to the pedal's shape (it's drawn only over the pedal's pixels) and tilts with it.
 */
class PedalImageView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    AppCompatImageView(context, attrs) {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
    }

    @ColorInt
    var fillColor: Int = Color.WHITE
        set(value) {
            field = value
            invalidate()
        }

    var level = 0f
        set(value) {
            val level = value.coerceIn(0f, 1f)
            if (field == level) return
            field = level
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        if (level <= 0f) {
            super.onDraw(canvas)
            return
        }
        // The fill is drawn over the pedal only: on a layer with the pedal, keeping its alpha
        val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        super.onDraw(canvas)
        fillPaint.color = fillColor
        canvas.drawRect(0f, height * (1f - level), width.toFloat(), height.toFloat(), fillPaint)
        canvas.restoreToCount(layer)
    }
}

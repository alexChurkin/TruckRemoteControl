package com.alexchurkin.truckremote.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt

// Vertical bar showing pedal pressing level
class PedalLevelView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TRACK_COLOR }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    @ColorInt
    var fillColor: Int = Color.WHITE
        set(value) {
            field = value
            invalidate()
        }

    private var level = 0f
    private var locked = false

    fun setState(level: Float, locked: Boolean) {
        if (this.level == level && this.locked == locked) return
        this.level = level
        this.locked = locked
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val radius = width / 2f
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, radius, radius, trackPaint)

        if (level > 0) {
            fillPaint.color = if (locked) LOCKED_COLOR else fillColor
            rect.set(0f, height * (1f - level), width.toFloat(), height.toFloat())
            canvas.drawRoundRect(rect, radius, radius, fillPaint)
        }
    }

    private companion object {
        const val TRACK_COLOR = 0x33FFFFFF
        const val LOCKED_COLOR = 0xFFFFB300.toInt()
    }
}

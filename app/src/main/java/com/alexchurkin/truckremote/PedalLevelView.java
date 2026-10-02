package com.alexchurkin.truckremote;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

//Vertical bar showing pedal pressing level
public class PedalLevelView extends View {

    private static final int TRACK_COLOR = 0x33FFFFFF;
    private static final int LOCKED_COLOR = 0xFFFFB300;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    @ColorInt
    private int fillColor = 0xFFFFFFFF;
    private float level;
    private boolean locked;

    public PedalLevelView(Context context) {
        this(context, null);
    }

    public PedalLevelView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        trackPaint.setColor(TRACK_COLOR);
    }

    public void setFillColor(@ColorInt int color) {
        fillColor = color;
        invalidate();
    }

    public void setState(float level, boolean locked) {
        if (this.level == level && this.locked == locked) return;
        this.level = level;
        this.locked = locked;
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        float width = getWidth();
        float height = getHeight();
        float radius = width / 2f;

        rect.set(0, 0, width, height);
        canvas.drawRoundRect(rect, radius, radius, trackPaint);

        if (level > 0) {
            fillPaint.setColor(locked ? LOCKED_COLOR : fillColor);
            rect.set(0, height * (1f - level), width, height);
            canvas.drawRoundRect(rect, radius, radius, fillPaint);
        }
    }
}

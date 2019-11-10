package com.alexchurkin.truckremote;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.WindowInsets;

import androidx.constraintlayout.widget.ConstraintLayout;

public class WithInsetsConstraintLayout extends ConstraintLayout {
    public WithInsetsConstraintLayout(Context context) {
        super(context);
    }

    public WithInsetsConstraintLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public WithInsetsConstraintLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @TargetApi(Build.VERSION_CODES.KITKAT_WATCH)
    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        int childCount = getChildCount();
        for (int index = 0; index < childCount; ++index)
            getChildAt(index).dispatchApplyWindowInsets(insets);
        return insets;
    }
}

package com.alexchurkin.truckremote.helpers;

import android.os.Build;
import android.view.Surface;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

public class ActivityExt {

    public static void enterFullscreen(AppCompatActivity activity) {
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            flags = flags | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        }
        activity.getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    public static boolean isReverseLandscape(AppCompatActivity activity) {
        return activity.getWindowManager().getDefaultDisplay().getRotation() == Surface.ROTATION_270;
    }
}
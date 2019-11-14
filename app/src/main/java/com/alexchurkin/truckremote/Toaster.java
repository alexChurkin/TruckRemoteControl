package com.alexchurkin.truckremote;

import android.content.Context;
import android.widget.Toast;

import androidx.annotation.StringRes;

public class Toaster {
    private static Context appContext;
    private static Toast lastToast;

    public static void initialize(Context context) {
        appContext = context;
    }

    public static void showToast(@StringRes int resId) {
        if (lastToast != null) {
            lastToast.cancel();
            lastToast = null;
        }
        lastToast = Toast.makeText(appContext, resId, Toast.LENGTH_SHORT);
        lastToast.show();
    }
}

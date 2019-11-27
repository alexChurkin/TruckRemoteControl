package com.alexchurkin.truckremote.helpers;

import android.content.Context;
import android.view.Gravity;
import android.widget.Toast;

import androidx.annotation.StringRes;

public class Toaster {
    private static Context appContext;
    private static Toast lastToast;

    public static int toastMargin;

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


    public static void showToastWithMargin(@StringRes int resId) {
        showToastWithMargin(appContext.getString(resId));
    }

    public static void showToastWithMargin(String text) {
        if (lastToast != null) {
            lastToast.cancel();
            lastToast = null;
        }
        lastToast = Toast.makeText(appContext, text, Toast.LENGTH_SHORT);
        lastToast.setGravity(Gravity.TOP, toastMargin / 2, 0);
        lastToast.show();
    }
}
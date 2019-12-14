package com.alexchurkin.truckremote.helpers;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDialog;

import static android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;

public class FullScreenActivityExt {

    public static void showAlert(AppCompatActivity activity, AppCompatDialog dialog) {
        dialog.getWindow().setFlags(FLAG_NOT_FOCUSABLE, FLAG_NOT_FOCUSABLE);
        dialog.show();
        dialog.getWindow().getDecorView().setSystemUiVisibility(
                activity.getWindow().getDecorView().getSystemUiVisibility()
        );
        dialog.getWindow().clearFlags(FLAG_NOT_FOCUSABLE);
    }
}

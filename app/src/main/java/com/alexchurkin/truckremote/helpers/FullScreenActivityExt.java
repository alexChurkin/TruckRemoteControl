package com.alexchurkin.truckremote.helpers;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import static android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;

public class FullScreenActivityExt {

    public static void showAlert(AppCompatActivity activity, AlertDialog dialog) {
        dialog.getWindow().setFlags(FLAG_NOT_FOCUSABLE, FLAG_NOT_FOCUSABLE);
        dialog.show();
        dialog.getWindow().getDecorView().setSystemUiVisibility(
                activity.getWindow().getDecorView().getSystemUiVisibility()
        );
        dialog.getWindow().clearFlags(FLAG_NOT_FOCUSABLE);
    }
}

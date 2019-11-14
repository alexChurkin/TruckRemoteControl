package com.alexchurkin.truckremote.general;

import android.app.Application;

import com.alexchurkin.truckremote.Toaster;

public class TruckRemote extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Prefs.initialize(this);
        Toaster.initialize(this);
    }
}

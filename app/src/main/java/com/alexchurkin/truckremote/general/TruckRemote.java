package com.alexchurkin.truckremote.general;

import android.app.Application;

public class TruckRemote extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Prefs.initialize(this);
    }
}

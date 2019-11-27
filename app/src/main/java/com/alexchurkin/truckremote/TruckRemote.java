package com.alexchurkin.truckremote;

import android.app.Application;

import com.alexchurkin.truckremote.helpers.Prefs;
import com.alexchurkin.truckremote.helpers.Toaster;

public class TruckRemote extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Prefs.initialize(this);
        Toaster.initialize(this);
    }
}

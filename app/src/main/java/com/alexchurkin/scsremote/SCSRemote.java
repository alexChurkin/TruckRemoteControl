package com.alexchurkin.scsremote;

import android.app.Application;

public class SCSRemote extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Prefs.initialize(this);
    }
}

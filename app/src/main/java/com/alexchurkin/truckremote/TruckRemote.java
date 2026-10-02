package com.alexchurkin.truckremote;

import static com.alexchurkin.truckremote.helpers.LogMan.logD;

import android.app.Application;

import com.alexchurkin.truckremote.helpers.AdManager;
import com.alexchurkin.truckremote.helpers.BillingMan;
import com.alexchurkin.truckremote.helpers.Prefs;
import com.alexchurkin.truckremote.helpers.Toaster;

public class TruckRemote extends Application {

    public static BillingMan billingMan;

    @Override
    public void onCreate() {
        super.onCreate();
        logD(">> Application: onCreate");
        Prefs.initialize(this);
        Toaster.initialize(this);

        AdManager.init(this);
        billingMan = BillingMan.getInstance(this);
    }
}

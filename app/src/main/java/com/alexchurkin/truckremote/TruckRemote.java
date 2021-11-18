package com.alexchurkin.truckremote;

import static com.alexchurkin.truckremote.helpers.BillingMan.PREF_AD_OFF;
import static com.alexchurkin.truckremote.helpers.LogMan.logD;

import androidx.multidex.MultiDexApplication;

import com.alexchurkin.truckremote.helpers.AdManager;
import com.alexchurkin.truckremote.helpers.BillingMan;
import com.alexchurkin.truckremote.helpers.Prefs;
import com.alexchurkin.truckremote.helpers.Toaster;

public class TruckRemote extends MultiDexApplication {

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

package com.alexchurkin.truckremote.helpers;

import static com.alexchurkin.truckremote.helpers.LogMan.logD;
import static com.alexchurkin.truckremote.helpers.LogMan.logThreadD;
import static java.lang.Math.min;

import android.app.Activity;
import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesResponseListener;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.SkuDetails;
import com.android.billingclient.api.SkuDetailsParams;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BillingMan implements LifecycleObserver,
        BillingClientStateListener, PurchasesUpdatedListener, PurchasesResponseListener {

    public static final String PREF_AD_OFF = "prefadsetting";
    public static final String PREF_ACKNOWLEDGED = "prefacknowledged";

    public static final String SKU_AD_OFF_ID = "add_off";

    //List of all known in-app SKUs that we have in Google Play account
    private final List<String> inAppSKUs = new ArrayList<>(
            Collections.singletonList(SKU_AD_OFF_ID));

    //1 Second
    private final static long RECONNECT_TIMER_START_MILLISECONDS = 1000L;
    //10 minutes
    private final static long RECONNECT_TIMER_MAX_TIME_MILLISECONDS = 1000L * 60L * 10L;


    interface SkuState {
        int NOT_PURCHASED = 0;
        int PURCHASED = 1;
    }

    private static BillingMan INSTANCE;

    public static BillingMan getInstance(Application app) {
        if (INSTANCE == null) INSTANCE = new BillingMan(app);
        return INSTANCE;
    }

    private final Application app;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    public SingleLiveEvent<Set<String>> livePurchaseEvent = new SingleLiveEvent<>();
    //(Not always restored, sometimes got from GP)
    public SingleLiveEvent<Set<String>> restorePurchaseEvent = new SingleLiveEvent<>();
    public SingleLiveEvent<Set<String>> returnedBackPurchaseEvent = new SingleLiveEvent<>();
    public SingleLiveEvent<Void> userCancelledEvent = new SingleLiveEvent<>();

    private BillingClient billingClient;

    //Here we will store sku details we got (for requesting the billing flow later)
    private final HashMap<String, SkuDetails> skuDetails = new HashMap<>();

    //Stores SKU -> Its state
    private final HashMap<String, Integer> localSkusStates = new HashMap<>();

    //Set of SKUs that should be acknowledged
    private final Set<String> skusToAcknowledge = new HashSet<>();

    private long reconnectMilliseconds = RECONNECT_TIMER_START_MILLISECONDS;


    private BillingMan(Application app) {
        this.app = app;

        //Fetching local purchases info
        for (String sku : inAppSKUs) localSkusStates.put(sku, SkuState.NOT_PURCHASED);
        boolean adDisablePurchased = Prefs.getBoolean(PREF_AD_OFF, false);
        if (adDisablePurchased) localSkusStates.put(SKU_AD_OFF_ID, SkuState.PURCHASED);
        logD("* Init local info: Ad disable purchased: " + adDisablePurchased);

        //If billing has purchases to acknowledge,
        //Connection will be opened on app startup
        if (!Prefs.getBoolean(PREF_ACKNOWLEDGED, true)) {
            billingClient = BillingClient.newBuilder(app)
                    .enablePendingPurchases().setListener(this).build();
            billingClient.startConnection(this);
            logD("* Starting billing connection... (purpose: acknowledgement) (init)");
        }
    }

    /* LifecycleEvents of SettingsFragment */

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    public void onCreate() {
        logD(">> onCreate");
        if (billingClient == null) {
            billingClient = BillingClient.newBuilder(app).enablePendingPurchases()
                    .setListener(this).build();
            billingClient.startConnection(this);
            logD("* Starting billing connection... (onCreate)");
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    public void onStart() {
        logD(">> onStart");
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    public void onResume() {
        logD(">> onResume");
        try {
            logD("* Querying purchases... (onResume)");
            queryPurchases();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    public void onDestroy() {
        logD(">> onDestroy");
        uiHandler.removeCallbacksAndMessages(null);
        if (billingClient != null) {
            try {
                billingClient.endConnection();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                billingClient = null;
            }
        }
        logD("* Billing connection closed; client destroyed (onDestroy)");
    }

    @Override
    public void onBillingServiceDisconnected() {
        logD("* Billing was disconnected. Retrying...");
        logThreadD();
        retryConnection();
    }

    @Override
    public void onBillingSetupFinished(@NonNull BillingResult result) {
        if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
            logD("* Billing connection established");
            logThreadD();
            reconnectMilliseconds = RECONNECT_TIMER_START_MILLISECONDS;

            //Let's query our skus from Google Play (to start purchase dialog later)
            querySkuDetails();
            //And query info about purchases user have
            //(purchases may have different states, not only PURCHASED!)
            queryPurchases();

        } else {
            retryConnection();
            logD("* Billing is not connected. Retrying...");
            logThreadD();
        }
    }

    //-> SKU Details came from Google Play
    private void onSkuDetails(BillingResult result, List<SkuDetails> skuDetailsList) {
        if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
            if (skuDetailsList != null && !skuDetailsList.isEmpty()) {
                for (SkuDetails skuDetails : skuDetailsList) {
                    this.skuDetails.put(skuDetails.getSku(), skuDetails);
                }
            } else {
                logD("* skuDetailsList is null or empty! Check that " +
                        "your SKUs are correctly published in GP Console.");
            }
        } else {
            logD("* Problem getting SKU Details. " +
                    "Debug message: ${result.debugMessage}");
        }
    }

    //User has purchased something new right now (while app was running)
    //And purchases was updated
    @Override
    public void onPurchasesUpdated(
            @NonNull BillingResult result, @Nullable List<Purchase> purchases) {
        int responseCode = result.getResponseCode();

        if (responseCode == BillingClient.BillingResponseCode.OK) {
            processPurchaseList(purchases, true);
        } else if (responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            logD("* Purchase flow was cancelled by the user");
            userCancelledEvent.call();
        }
    }

    //Here will come purchases queried manually
    //(purchases could change even when app wasn't running)
    @Override
    public void onQueryPurchasesResponse(
            @NonNull BillingResult result, @NonNull List<Purchase> purchases) {
        int responseCode = result.getResponseCode();

        if (responseCode == BillingClient.BillingResponseCode.OK) {
            processPurchaseList(purchases, false);
        } else {
            logD("* Problem getting purchases. " +
                    "Debug message: " + result.getDebugMessage());
        }
    }

    //-> We got purchases (isLiveUpdate = true means that some purchase(s) happened right now)
    //Non-UI thread method!
    @WorkerThread
    private void processPurchaseList(List<Purchase> purchases, Boolean isLiveUpdate) {
        logD("* ---- Processing Google Play purchases list");
        logThreadD();

        //What new was purchased (depending on our previous local state)
        Set<String> newPurchasedSkus = new HashSet<>();

        //What was returned back (depending on our previous local state)
        Set<String> returnedSkus = new HashSet<>();
        for (Map.Entry<String, Integer> entry : localSkusStates.entrySet()) {
            if (entry.getValue() == SkuState.PURCHASED) {
                returnedSkus.add(entry.getKey());
            }
        }

        //Empty purchases list came from Google Play
        if (purchases == null || purchases.isEmpty()) {
            logD("* Purchases list is null or empty");
            Prefs.putBoolean(PREF_ACKNOWLEDGED, true);
        }
        //Non-empty purchases list came from Google Play
        else {
            for (Purchase purchase : purchases) {
                List<String> pSkus = purchase.getSkus();
                for (String purchaseSku : pSkus) {
                    logD("* -- Can see SKU: " + purchaseSku);
                    int newSkuState = skuStateFromPurchase(purchase);
                    logD("* newSkuState = " + newSkuState);
                    int localState;

                    //New SKU was purchased
                    if (localSkusStates.get(purchaseSku) != newSkuState
                            && newSkuState == SkuState.PURCHASED) {
                        newPurchasedSkus.add(purchaseSku);
                        logD("* newPurchasedSkus.add(" + purchaseSku + ")");
                    }
                    //Was purchased and remains purchased
                    else if (localSkusStates.get(purchaseSku) == newSkuState &&
                            newSkuState == SkuState.PURCHASED) {
                        returnedSkus.remove(purchaseSku);
                    }

                    //Updating local SKU state
                    localSkusStates.put(purchaseSku, newSkuState);
                }

                //If purchase is purchased, we should do acknowledgement if needed
                if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                    logD("* This purchase is purchased");

                    if (!purchase.isAcknowledged()) {
                        logD("* And not acknowledged. Acknowledging...");
                        Prefs.putBoolean(PREF_ACKNOWLEDGED, false);
                        skusToAcknowledge.addAll(purchase.getSkus());
                        //Acknowledging (PREF_ACKNOWLEDGED updates after every acknowledge)
                        acknowledgePurchase(purchase);
                    } else {
                        logD("* And acknowledged");
                        skusToAcknowledge.removeAll(purchase.getSkus());
                    }
                }
            }
        }

        logD("* PREF_ACKNOWLEDGED = " + Prefs.getBoolean(PREF_ACKNOWLEDGED, true));
        logD("* newPurchasedSkus.size = " + newPurchasedSkus.size());
        logD("* disappearedSkus.size = " + returnedSkus.size());

        //App should give something to the user
        if (!newPurchasedSkus.isEmpty()) {
            if (newPurchasedSkus.contains(SKU_AD_OFF_ID))
                Prefs.putBoolean(PREF_AD_OFF, true);

            if (isLiveUpdate) {
                logD("* Live update: contains SKU_AD_OFF_ID: "
                        + newPurchasedSkus.contains(SKU_AD_OFF_ID));
                uiHandler.post(() -> livePurchaseEvent.setValue(newPurchasedSkus));
            } else {
                logD("* Restored SKUs: contain SKU_SUPPORT_AUTHOR:"
                        + newPurchasedSkus.contains(SKU_AD_OFF_ID));
                uiHandler.post(() -> restorePurchaseEvent.setValue(newPurchasedSkus));
            }
        }

        //App should take something away from the user
        if (!returnedSkus.isEmpty()) {
            for (String rSku : returnedSkus) localSkusStates.put(rSku, SkuState.NOT_PURCHASED);

            if (returnedSkus.contains(SKU_AD_OFF_ID))
                Prefs.putBoolean(PREF_AD_OFF, false);
            uiHandler.post(() -> returnedBackPurchaseEvent.setValue(returnedSkus));
        }
    }

    @MainThread
    public void launchPurchaseFlow(Activity activity, String sku) {
        SkuDetails details = skuDetails.get(sku);

        if (details == null) {
            logD("Launching  purchase flow failed: unknown SKU");
        } else {
            BillingFlowParams flowParams = BillingFlowParams.newBuilder()
                    .setSkuDetails(details)
                    .build();
            billingClient.launchBillingFlow(activity, flowParams);
            Prefs.putBoolean(PREF_ACKNOWLEDGED, false);
        }
    }


    /* ......................................................................... */

    //Step 1 after connection - querying SKU details for future purchases
    private void querySkuDetails() {
        SkuDetailsParams.Builder pBuilder = SkuDetailsParams.newBuilder()
                .setSkusList(inAppSKUs).setType(BillingClient.SkuType.INAPP);
        billingClient.querySkuDetailsAsync(pBuilder.build(), this::onSkuDetails);
    }

    //Step 2 after connection - querying active purchases
    private void queryPurchases() {
        if (billingClient != null) {
            billingClient.queryPurchasesAsync(
                    BillingClient.SkuType.INAPP, this);
        }
    }

    private void acknowledgePurchase(Purchase purchase) {
        AcknowledgePurchaseParams params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.getPurchaseToken()).build();

        billingClient.acknowledgePurchase(params, result -> {
            if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                logD("* Purchase was acknowledged: " + purchase);
                skusToAcknowledge.removeAll(purchase.getSkus());

                //After every success acknowledgement we will update this value
                Prefs.putBoolean(PREF_ACKNOWLEDGED, skusToAcknowledge.isEmpty());
            } else {
                logD("* Acknowledgement failed: responseCode = "
                        + result.getResponseCode() + "; Debug message: "
                        + result.getDebugMessage());
            }
        });
    }

    //Retries connection after some time
    private void retryConnection() {
        uiHandler.postDelayed(
                (Runnable) () -> billingClient.startConnection(this),
                reconnectMilliseconds
        );

        reconnectMilliseconds = min(
                reconnectMilliseconds * 2,
                RECONNECT_TIMER_MAX_TIME_MILLISECONDS
        );
    }

    private int skuStateFromPurchase(Purchase purchase) {
        if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
            return SkuState.PURCHASED;
        } else return SkuState.NOT_PURCHASED;
    }
}
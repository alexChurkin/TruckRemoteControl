package com.alexchurkin.truckremote.fragment;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.alexchurkin.truckremote.R;
import com.alexchurkin.truckremote.general.Prefs;
import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.SkuDetails;
import com.android.billingclient.api.SkuDetailsParams;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import static com.alexchurkin.truckremote.Toaster.showToast;

public class SettingsFragment extends PreferenceFragmentCompat implements PurchasesUpdatedListener, BillingClientStateListener {

    public static final String PREF_KEY_ADDOFF = "prefadsetting";
    private static final String SKU_AD_OFF_ID = "add_off";

    private static final String KEY_SHOWED_ABOUT = "ShowedAbout";
    private boolean showedAbout;

    private BillingClient mBillingClient;
    private HashMap<String, SkuDetails> mSkuDetailsMap = new HashMap<>();

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.settings, rootKey);
        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.findPreference("removeAds").setOnPreferenceClickListener(preference -> {
            launchBilling();
            return true;
        });
        preferenceManager.findPreference("github").setOnPreferenceClickListener(preference -> {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(getString(R.string.github_link)));
            startActivity(intent);
            return true;
        });
        preferenceManager.findPreference("about").setOnPreferenceClickListener(preference -> {
            showDialogAbout();
            showedAbout = true;
            return true;
        });
        if (Prefs.getBoolean(PREF_KEY_ADDOFF, false)) {
            getPreferenceManager().findPreference("removeAds").setVisible(false);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (savedInstanceState != null) {
            showedAbout = savedInstanceState.getBoolean(KEY_SHOWED_ABOUT, false);
            if (showedAbout) {
                showDialogAbout();
            }
        }
        mBillingClient = BillingClient.newBuilder(getActivity())
                .enablePendingPurchases().setListener(this).build();
        mBillingClient.startConnection(this);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_SHOWED_ABOUT, showedAbout);
    }

    private void showDialogAbout() {
        View aboutLayout = View.inflate(getContext(), R.layout.text_view, null);
        TextView textView = aboutLayout.findViewById(R.id.textView);
        textView.setText(R.string.about_app_text);

        new AlertDialog.Builder(Objects.requireNonNull(getContext()))
                .setTitle(R.string.about_app)
                .setIcon(R.mipmap.ic_launcher)
                .setView(aboutLayout)
                .setPositiveButton(R.string.close, (dialogInterface, i) -> showedAbout = false)
                .setOnDismissListener(dialogInterface -> showedAbout = false)
                .create()
                .show();
    }

    private void launchBilling() {
        BillingFlowParams flowParams = BillingFlowParams.newBuilder()
                .setSkuDetails(mSkuDetailsMap.get(SKU_AD_OFF_ID))
                .build();
        mBillingClient.launchBillingFlow(getActivity(), flowParams);
    }

    @Override
    public void onBillingSetupFinished(BillingResult result) {
        if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
            querySkuDetails();
            List<Purchase> purchases = queryPurchases();

            if (purchases != null) {
                for (Purchase purchase : purchases) {

                    if (purchase.getSku().equals(SKU_AD_OFF_ID)) {
                        if (!purchase.isAcknowledged()) {
                            acknowledgePurchase(purchase, R.string.purchase_success);
                        } else if(!Prefs.getBoolean(PREF_KEY_ADDOFF, false)) {
                            Prefs.putBoolean(PREF_KEY_ADDOFF, true);
                            showToast(R.string.purchase_restored);
                            getPreferenceManager().findPreference("removeAds").setVisible(false);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onBillingServiceDisconnected() {
    }

    @Override
    public void onPurchasesUpdated(BillingResult billingResult, @Nullable List<Purchase> purchases) {
        switch (billingResult.getResponseCode()) {
            case BillingClient.BillingResponseCode.OK:
                for (Purchase purchase : purchases) {
                    if (purchase.getSku().equals(SKU_AD_OFF_ID) && !purchase.isAcknowledged()) {
                        acknowledgePurchase(purchase, R.string.purchase_success);
                    }
                }
                break;
            case BillingClient.BillingResponseCode.USER_CANCELED:
                showToast(R.string.purchase_cancelled);
                break;
        }
    }

    private void acknowledgePurchase(Purchase purchase, @StringRes int msgRes) {
        AcknowledgePurchaseParams params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.getPurchaseToken())
                .build();
        mBillingClient.acknowledgePurchase(params, billingResult -> {
            Prefs.putBoolean(PREF_KEY_ADDOFF, true);
            showToast(msgRes);
            getPreferenceManager().findPreference("removeAds").setVisible(false);
        });
    }

    private void querySkuDetails() {
        ArrayList<String> skuList = new ArrayList<>();
        skuList.add(SKU_AD_OFF_ID);

        SkuDetailsParams.Builder params = SkuDetailsParams.newBuilder()
                .setSkusList(skuList)
                .setType(BillingClient.SkuType.INAPP);

        mBillingClient.querySkuDetailsAsync(params.build(), (billingResult, skuDetailsList) -> {
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                for (SkuDetails skuDetails : skuDetailsList) {
                    mSkuDetailsMap.put(skuDetails.getSku(), skuDetails);
                }
            }
        });
    }

    private List<Purchase> queryPurchases() {
        Purchase.PurchasesResult purchasesResult =
                mBillingClient.queryPurchases(BillingClient.SkuType.INAPP);
        return purchasesResult.getPurchasesList();
    }
}
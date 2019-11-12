package com.alexchurkin.truckremote;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.SkuDetails;
import com.android.billingclient.api.SkuDetailsParams;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class SettingsFragment extends PreferenceFragmentCompat implements PurchasesUpdatedListener, BillingClientStateListener {

    private static final String SKU_SUPPORT_AUTHOR_ID = "support_the_author";

    private static final String KEY_SHOWED_ABOUT = "ShowedAbout";
    private boolean showedAbout;

    private BillingClient mBillingClient;
    private HashMap<String, SkuDetails> mSkuDetailsMap = new HashMap<>();

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (savedInstanceState != null) {
            showedAbout = savedInstanceState.getBoolean(KEY_SHOWED_ABOUT, false);
            if (showedAbout) {
                showDialogAbout();
            }
        }
        mBillingClient = BillingClient.newBuilder(getContext())
                .enablePendingPurchases().setListener(this).build();
        mBillingClient.startConnection(this);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.settings, rootKey);
        PreferenceManager preferenceManager = getPreferenceManager();
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
        preferenceManager.findPreference("support").setOnPreferenceClickListener(preference -> {
            launchBilling();
            return true;
        });
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
                .setSkuDetails(mSkuDetailsMap.get(SKU_SUPPORT_AUTHOR_ID))
                .build();
        mBillingClient.launchBillingFlow(getActivity(), flowParams);
    }

    @Override
    public void onBillingSetupFinished(BillingResult billingResult) {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
            querySkuDetails();
            List<Purchase> purchases = queryPurchases();
            for (Purchase purchase : purchases) {
                //and not purchased value (in prefs)
                if (purchase.getSku().equals(SKU_SUPPORT_AUTHOR_ID)) {
                    showToast(R.string.purchase_restored);
                    getPreferenceManager().findPreference("support").setVisible(false);
                    //TODO Restore purchase
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
                    if (purchase.getSku().equals(SKU_SUPPORT_AUTHOR_ID)) {
                        ConsumeParams consParams = ConsumeParams.newBuilder()
                                .setPurchaseToken(purchase.getPurchaseToken())
                                .build();
                        mBillingClient.consumeAsync(consParams, (billResult, purchaseToken) -> {
                            showToast(R.string.purchase_success);
                            getPreferenceManager().findPreference("support").setVisible(false);
                            //TODO remove ad in prefs and remove item from settings
                        });
                    }
                }
                break;
            case BillingClient.BillingResponseCode.USER_CANCELED:
                showToast(R.string.purchase_cancelled);
                break;
        }
    }

    private void querySkuDetails() {
        ArrayList<String> skuList = new ArrayList<>();
        skuList.add(SKU_SUPPORT_AUTHOR_ID);

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

    private void showToast(@StringRes int resId) {
        Toast toast = Toast.makeText(getContext(), resId, Toast.LENGTH_SHORT);
        toast.show();
    }
}

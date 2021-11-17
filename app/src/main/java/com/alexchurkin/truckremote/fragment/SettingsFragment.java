package com.alexchurkin.truckremote.fragment;

import static com.alexchurkin.truckremote.TruckRemote.billingMan;
import static com.alexchurkin.truckremote.helpers.BillingMan.PREF_AD_OFF;
import static com.alexchurkin.truckremote.helpers.BillingMan.SKU_AD_OFF_ID;
import static com.alexchurkin.truckremote.helpers.LogMan.logD;
import static com.alexchurkin.truckremote.helpers.Toaster.showToast;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.alexchurkin.truckremote.BuildConfig;
import com.alexchurkin.truckremote.R;
import com.alexchurkin.truckremote.helpers.AdManager;
import com.alexchurkin.truckremote.helpers.Prefs;

public class SettingsFragment extends PreferenceFragmentCompat {

    private static final String KEY_SHOWED_ABOUT = "ShowedAbout";
    private boolean showedAbout;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getLifecycle().addObserver(billingMan);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.settings, rootKey);
        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.findPreference("about")
                .setSummary(getString(R.string.version) + " " + BuildConfig.VERSION_NAME);
        preferenceManager.findPreference("removeAds").setOnPreferenceClickListener(preference -> {
            billingMan.launchPurchaseFlow(requireActivity(), SKU_AD_OFF_ID);
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
        if (Prefs.getBoolean(PREF_AD_OFF, false)) {
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

        billingMan.livePurchaseEvent.observe(requireActivity(), it -> {
            logD("* Live purchases event received");
            if (it.contains(SKU_AD_OFF_ID)) {
                logD("* Contains SKU_AD_OFF_ID");
                showToast(R.string.purchase_success);
                getPreferenceManager().findPreference("removeAds").setVisible(false);
            }
        });
        billingMan.restorePurchaseEvent.observe(requireActivity(), it -> {
            logD("* Restored purchases event received");
            if (it.contains(SKU_AD_OFF_ID)) {
                logD("* Contains SKU_AD_OFF_ID");
                showToast(R.string.purchase_restored);
                getPreferenceManager().findPreference("removeAds").setVisible(false);
            }
        });
        billingMan.returnedBackPurchaseEvent.observe(requireActivity(), it -> {
            logD("* Returned purchases event received");
            if (it.contains(SKU_AD_OFF_ID)) {
                logD("* Contains SKU_AD_OFF_ID");
                showToast(R.string.purchase_returned);
                getPreferenceManager().findPreference("removeAds").setVisible(true);
            }
        });
        billingMan.userCancelledEvent.observe(requireActivity(), someVoid -> {
            logD("* User cancelled purchase flow");
            showToast(R.string.purchase_cancelled);
        });

        AdManager.tryShowFullscreenAd(requireActivity());
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

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.about_app)
                .setIcon(R.mipmap.ic_launcher)
                .setView(aboutLayout)
                .setPositiveButton(R.string.close, (dialogInterface, i) -> showedAbout = false)
                .setOnDismissListener(dialogInterface -> showedAbout = false)
                .create()
                .show();
    }
}
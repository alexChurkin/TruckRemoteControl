package com.alexchurkin.truckremote;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

public class SettingsFragment extends PreferenceFragmentCompat {

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

            return true;
        });
    }
}

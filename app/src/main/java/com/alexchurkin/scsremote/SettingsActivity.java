package com.alexchurkin.scsremote;

import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;

public class SettingsActivity extends PreferenceActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.settings);
        getPreferenceScreen().findPreference("serverIP").setOnPreferenceClickListener(serverIPClickListener);
        getPreferenceScreen().findPreference("serverPort").setOnPreferenceClickListener(serverPortClickListener);
    }

    Preference.OnPreferenceClickListener serverIPClickListener = new OnPreferenceClickListener() {
        public boolean onPreferenceClick(Preference preference) {
            Intent i = new Intent(SettingsActivity.this, DefaultServerIPActivity.class);
            startActivity(i);
            return true;
        }
    };

    Preference.OnPreferenceClickListener serverPortClickListener = new OnPreferenceClickListener() {
        public boolean onPreferenceClick(Preference preference) {
            Intent i = new Intent(SettingsActivity.this, DefaultServerPortActivity.class);
            startActivity(i);
            return true;
        }
    };
}
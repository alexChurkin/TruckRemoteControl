package com.alexchurkin.truckremote;

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

import java.util.Objects;

public class SettingsFragment extends PreferenceFragmentCompat {

    private static final String KEY_SHOWED_ABOUT = "ShowedAbout";
    private boolean showedAbout;

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (savedInstanceState != null) {
            showedAbout = savedInstanceState.getBoolean(KEY_SHOWED_ABOUT, false);
            if (showedAbout) {
                showDialogAbout();
            }
        }
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
}

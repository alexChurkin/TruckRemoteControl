package com.alexchurkin.truckremote.activity;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.alexchurkin.truckremote.R;
import com.alexchurkin.truckremote.SettingsFragment;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.container, new SettingsFragment())
                    .commit();
        }
    }
}
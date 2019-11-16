package com.alexchurkin.truckremote.activity;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.alexchurkin.truckremote.R;
import com.alexchurkin.truckremote.fragment.GuideFragment;

import static com.alexchurkin.truckremote.fragment.GuideFragment.GUIDE_NUMBER;

public class GuideActivity extends AppCompatActivity {

    private int guideNumber;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_guide);

        if (savedInstanceState == null) {
            setGuide(0);
        } else {
            guideNumber = savedInstanceState.getInt(GUIDE_NUMBER);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(GUIDE_NUMBER, guideNumber);
    }

    private void setGuide(int guideNumber) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.container, GuideFragment.createWithGuide(guideNumber))
                .commit();
    }
}
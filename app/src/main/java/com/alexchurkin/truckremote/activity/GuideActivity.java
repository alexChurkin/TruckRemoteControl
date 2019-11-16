package com.alexchurkin.truckremote.activity;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.fragment.app.FragmentTransaction;

import com.alexchurkin.truckremote.R;
import com.alexchurkin.truckremote.fragment.GuideFragment;

import static com.alexchurkin.truckremote.fragment.GuideFragment.GUIDE_NUMBER;

public class GuideActivity extends AppCompatActivity {

    public AppCompatImageButton mButtonPrev, mButtonNext;

    private int guideNumber;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_guide);
        mButtonPrev = findViewById(R.id.buttonPrev);
        mButtonNext = findViewById(R.id.buttonNext);

        mButtonPrev.setOnClickListener(view -> {
            guideNumber--;
            setGuide(guideNumber, R.anim.right_out, R.anim.left_in);
            if (guideNumber == 0) {
                mButtonPrev.setVisibility(View.GONE);
            }
        });

        mButtonNext.setOnClickListener(view -> {
            guideNumber++;
            if (guideNumber == 1) {
                mButtonPrev.setVisibility(View.VISIBLE);
                setGuide(guideNumber, R.anim.left_out, R.anim.right_in);
            } else finish();
        });

        if (savedInstanceState == null) {
            setGuide(0, 0, 0);
        } else {
            guideNumber = savedInstanceState.getInt(GUIDE_NUMBER);
            if (guideNumber == 1) {
                mButtonPrev.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(GUIDE_NUMBER, guideNumber);
    }

    private void setGuide(int guideNumber, int animationOut, int animationIn) {
        FragmentTransaction transaction = getSupportFragmentManager()
                .beginTransaction();

        if (animationIn != 0 && animationOut != 0) {
            transaction.setCustomAnimations(animationIn, animationOut);
        }
        transaction
                .replace(R.id.container, GuideFragment.createWithGuide(guideNumber))
                .commit();
    }
}
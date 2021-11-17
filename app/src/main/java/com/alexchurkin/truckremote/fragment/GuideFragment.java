package com.alexchurkin.truckremote.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.alexchurkin.truckremote.R;

public class GuideFragment extends Fragment {

    public static final String GUIDE_NUMBER = "GuideNumber";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        int guideNumber = requireArguments().getInt(GUIDE_NUMBER);
        if(guideNumber == 0) {
            return inflater.inflate(R.layout.guide_0, container, false);
        } else if(guideNumber == 1) {
            return inflater.inflate(R.layout.guide_1, container, false);
        } else return null;
    }

    public static GuideFragment createWithGuide(int guideNumber) {
        GuideFragment fragment = new GuideFragment();
        Bundle args = new Bundle();
        args.putInt(GUIDE_NUMBER, guideNumber);
        fragment.setArguments(args);
        return fragment;
    }
}
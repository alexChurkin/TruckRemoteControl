package com.alexchurkin.truckremote.dialog;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.DialogFragment;

import com.alexchurkin.truckremote.R;
import com.alexchurkin.truckremote.helpers.ActivityExt;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import static android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;

public class MenuDialogFragment extends DialogFragment implements View.OnClickListener {

    private ItemClickListener mListener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View dialogView = inflater.inflate(R.layout.dialog_menu, container, false);
        setMaxPeekHeight();
        ActivityExt.enterFullscreen((AppCompatActivity) getActivity());
        return dialogView;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        return new BottomSheetDialog(requireContext(), R.style.BottomSheetDialogTheme);
    }

    @Override
    public void setupDialog(@NonNull Dialog dialog, int style) {
        super.setupDialog(dialog, style);
        getDialog().getWindow().setFlags(FLAG_NOT_FOCUSABLE, FLAG_NOT_FOCUSABLE);
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT_WATCH)
    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        View view = getView();
        view.setFitsSystemWindows(false);
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> insets);
        ViewParent parent = view.getParent();

        while(parent instanceof View) {
            View parentView = ((View) parent);
            parentView.setFitsSystemWindows(false);
            ViewCompat.setOnApplyWindowInsetsListener(parentView, (v, insets) -> insets);
            parent = parentView.getParent();
        }
    }

    @Override
    public void onViewCreated(@NonNull View menuView, @Nullable Bundle savedInstanceState) {
        menuView.findViewById(R.id.autoConnectItem).setOnClickListener(this);
        menuView.findViewById(R.id.defaultConnectItem).setOnClickListener(this);
        menuView.findViewById(R.id.disconnectItem).setOnClickListener(this);
        menuView.findViewById(R.id.calibrateItem).setOnClickListener(this);
        menuView.findViewById(R.id.settingsItem).setOnClickListener(this);
        menuView.findViewById(R.id.instructionItem).setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.autoConnectItem:
                mListener.onItemClick(0);
                break;
            case R.id.defaultConnectItem:
                mListener.onItemClick(1);
                break;
            case R.id.disconnectItem:
                mListener.onItemClick(2);
                break;
            case R.id.instructionItem:
                mListener.onItemClick(3);
                break;
            case R.id.calibrateItem:
                mListener.onItemClick(4);
                break;
            case R.id.settingsItem:
                mListener.onItemClick(5);
                break;
        }
        dismiss();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof ItemClickListener) {
            mListener = (ItemClickListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement ItemClickListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener.onItemClick(-1);
        mListener = null;
    }

    public void setMaxPeekHeight() {
        getDialog().setOnShowListener(dialog -> {
            BottomSheetDialog bottomSheetDialog = (BottomSheetDialog) dialog;
            FrameLayout bottomSheet = bottomSheetDialog.findViewById(R.id.design_bottom_sheet);
            assert bottomSheet != null;
            BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(bottomSheet);
            behavior.setPeekHeight(bottomSheet.getHeight());
        });
    }

    public interface ItemClickListener {
        void onItemClick(int position);
    }
}

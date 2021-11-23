package com.alexchurkin.truckremote.dialog;

import static android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
import static java.util.Objects.requireNonNull;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.DialogFragment;

import com.alexchurkin.truckremote.R;
import com.alexchurkin.truckremote.helpers.ActivityTools;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

public class MenuDialogFragment extends DialogFragment implements View.OnClickListener {

    private ItemClickListener mListener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View dialogView = inflater.inflate(R.layout.dialog_menu_default, container, false);
        setMaxPeekHeight();
        ActivityTools.enterFullscreen((AppCompatActivity) getActivity());
        return dialogView;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = new
                BottomSheetDialog(requireContext(), R.style.BottomSheetDialogTheme);
        dialog.getWindow().setFlags(FLAG_NOT_FOCUSABLE, FLAG_NOT_FOCUSABLE);
        return dialog;
    }

    @Override
    public void onViewCreated(@NonNull View menuView, @Nullable Bundle savedInstanceState) {
        menuView.findViewById(R.id.autoConnectItem).setOnClickListener(this);
        menuView.findViewById(R.id.defaultConnectItem).setOnClickListener(this);
        menuView.findViewById(R.id.disconnectItem).setOnClickListener(this);
        menuView.findViewById(R.id.calibrateItem).setOnClickListener(this);
        menuView.findViewById(R.id.settingsItem).setOnClickListener(this);
        menuView.findViewById(R.id.instructionItem).setOnClickListener(this);

        View view = requireView();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> insets);
        ViewParent parent = view.getParent();

        while (parent instanceof View) {
            View parentView = ((View) parent);
            parentView.setFitsSystemWindows(false);
            ViewCompat.setOnApplyWindowInsetsListener(parentView, (v, insets) -> insets);
            parent = parentView.getParent();
        }
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();

        if (id == R.id.autoConnectItem) {
            mListener.onItemClick(0);

        } else if (id == R.id.defaultConnectItem) {
            mListener.onItemClick(1);

        } else if (id == R.id.disconnectItem) {
            mListener.onItemClick(2);

        } else if (id == R.id.instructionItem) {
            mListener.onItemClick(3);

        } else if (id == R.id.calibrateItem) {
            mListener.onItemClick(4);

        } else if (id == R.id.settingsItem) {
            mListener.onItemClick(5);

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
        requireNonNull(getDialog()).setOnShowListener(dialog -> {
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

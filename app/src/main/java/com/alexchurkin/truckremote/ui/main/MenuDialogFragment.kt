package com.alexchurkin.truckremote.ui.main

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.fragment.app.DialogFragment
import com.alexchurkin.truckremote.R
import com.alexchurkin.truckremote.databinding.DialogMenuBinding
import com.alexchurkin.truckremote.util.enterFullscreen
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

class MenuDialogFragment : DialogFragment() {

    enum class Item {
        AutoConnect,
        DefaultConnect,
        Disconnect,
        Guide,
        Calibration,
        Settings,
    }

    interface Listener {
        fun onMenuItemSelected(item: Item)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        BottomSheetDialog(requireContext(), R.style.BottomSheetDialogTheme).apply {
            // Not focusable dialog doesn't show system bars over the fullscreen activity
            window?.setFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            )
            setOnShowListener {
                // The whole menu is shown at once, also in landscape
                findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)?.let {
                    BottomSheetBehavior.from(it).peekHeight = it.height
                }
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        requireActivity().enterFullscreen()
        val binding = DialogMenuBinding.inflate(inflater, container, false)
        mapOf(
            binding.autoConnectItem to Item.AutoConnect,
            binding.defaultConnectItem to Item.DefaultConnect,
            binding.disconnectItem to Item.Disconnect,
            binding.instructionItem to Item.Guide,
            binding.calibrateItem to Item.Calibration,
            binding.settingsItem to Item.Settings,
        ).forEach { (view, item) ->
            view.setOnClickListener {
                (activity as? Listener)?.onMenuItemSelected(item)
                dismiss()
            }
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Bottom sheet containers would add system bars padding, the menu should stay at the very bottom
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets -> insets }
        var parent = view.parent
        while (parent is View) {
            parent.fitsSystemWindows = false
            ViewCompat.setOnApplyWindowInsetsListener(parent) { _, insets -> insets }
            parent = parent.parent
        }
    }

    companion object {
        const val TAG = "Menu"
    }
}

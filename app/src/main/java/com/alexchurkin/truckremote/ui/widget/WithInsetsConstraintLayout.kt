package com.alexchurkin.truckremote.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.WindowInsets
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.children

// Passes window insets to every child (ConstraintLayout stops at the first one which consumes them)
class WithInsetsConstraintLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr) {

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        children.forEach { it.dispatchApplyWindowInsets(insets) }
        return insets
    }
}

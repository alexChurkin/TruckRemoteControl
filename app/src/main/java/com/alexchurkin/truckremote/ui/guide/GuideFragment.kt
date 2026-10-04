package com.alexchurkin.truckremote.ui.guide

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.alexchurkin.truckremote.R

class GuideFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val layout = PAGE_LAYOUTS[requireArguments().getInt(ARG_PAGE)]
        return inflater.inflate(layout, container, false)
    }

    companion object {
        private const val ARG_PAGE = "GuideNumber"
        private val PAGE_LAYOUTS = intArrayOf(R.layout.guide_0, R.layout.guide_1)
        val PAGES_COUNT = PAGE_LAYOUTS.size

        fun newInstance(page: Int) = GuideFragment().apply { arguments = Bundle().apply { putInt(ARG_PAGE, page) } }
    }
}

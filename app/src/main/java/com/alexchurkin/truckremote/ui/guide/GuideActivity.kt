package com.alexchurkin.truckremote.ui.guide

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.alexchurkin.truckremote.R
import com.alexchurkin.truckremote.databinding.ActivityGuideBinding
import com.alexchurkin.truckremote.util.applySystemBarsPadding

class GuideActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGuideBinding
    private var page = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGuideBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.guideRoot.applySystemBarsPadding()

        page = savedInstanceState?.getInt(KEY_PAGE) ?: 0
        if (savedInstanceState == null) showPage(animated = false)
        binding.buttonPrev.isVisible = page > 0

        binding.buttonPrev.setOnClickListener {
            page--
            showPage(animated = true, forward = false)
        }
        binding.buttonNext.setOnClickListener {
            if (page == GuideFragment.PAGES_COUNT - 1) {
                finish()
            } else {
                page++
                showPage(animated = true, forward = true)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_PAGE, page)
    }

    private fun showPage(animated: Boolean, forward: Boolean = true) {
        binding.buttonPrev.isVisible = page > 0
        val transaction = supportFragmentManager.beginTransaction()
        if (animated) {
            if (forward) {
                transaction.setCustomAnimations(R.anim.right_in, R.anim.left_out)
            } else {
                transaction.setCustomAnimations(R.anim.left_in, R.anim.right_out)
            }
        }
        transaction.replace(R.id.container, GuideFragment.newInstance(page)).commit()
    }

    private companion object {
        const val KEY_PAGE = "GuideNumber"
    }
}

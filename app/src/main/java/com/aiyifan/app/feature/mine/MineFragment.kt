package com.aiyifan.app.feature.mine

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import com.aiyifan.app.R
import com.aiyifan.app.core.data.AppGraph
import com.aiyifan.app.core.ui.ThemeMode
import com.aiyifan.app.core.ui.ThemePreferenceStore
import com.aiyifan.app.databinding.FragmentMineBinding
import com.aiyifan.app.feature.collection.CollectionActivity
import com.aiyifan.app.feature.history.HistoryActivity
import com.aiyifan.app.feature.hot.HotActivity
import com.aiyifan.app.feature.localmedia.LocalMediaActivity
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MineFragment : Fragment() {
    private var _binding: FragmentMineBinding? = null
    private val binding get() = _binding!!

    private lateinit var slideshow: HeaderSlideshow
    private var images = emptyList<String>()
    private var carousel: HeaderCarousel? = null
    private var headerExpanded = false
    private var topInset = 0
    private var windowRoot: View? = null
    private val windowLayoutListener = View.OnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
        if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
            configureExpansion(forceReload = right - left != oldRight - oldLeft)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        headerExpanded = savedInstanceState?.getBoolean("header_expanded") ?: false
        images = requireContext().assets.list("mine_header").orEmpty()
            .filter { it.endsWith(".webp") }.sorted()
        slideshow = HeaderSlideshow(
            images.size,
            savedInstanceState?.getInt("header_index") ?: 0,
            savedInstanceState?.getBoolean("header_paused") ?: false,
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("header_index", slideshow.index)
        outState.putBoolean("header_paused", slideshow.paused)
        outState.putBoolean("header_expanded", headerExpanded)
        super.onSaveInstanceState(outState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMineBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        windowRoot = requireActivity().window.decorView
        configureExpansion(reload = false)
        binding.mineHeader.restoreExpanded(headerExpanded)
        headerExpanded = binding.mineHeader.expanded
        binding.mineHeader.isPageAtTop = { _binding?.root?.scrollY == 0 }
        binding.mineHeader.scrollPageTowardTop = { distance ->
            val page = _binding?.root
            val before = page?.scrollY ?: 0
            page?.scrollTo(0, (before - distance).coerceAtLeast(0))
            before - (page?.scrollY ?: 0)
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.mineHeader) { _, insets ->
            topInset = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()).top
            configureExpansion()
            insets
        }
        ViewCompat.requestApplyInsets(binding.mineHeader)
        carousel = HeaderCarousel(
            Glide.with(this), images, slideshow, binding.headerImage, binding.headerNextImage,
            { binding.mineHeader.expandedHeightPx }, ::updatePlayback,
        )
        binding.mineHeader.listener = object : HeaderGestureLayout.Listener {
            override fun onTouchStart() { carousel?.beginTouch() }
            override fun onHorizontalDrag(offset: Float) { carousel?.drag(offset) }
            override fun onTouchFinish(switchImage: Boolean) { carousel?.finishTouch(switchImage) }
            override fun onExpandedChanged(expanded: Boolean) { headerExpanded = expanded }
        }
        windowRoot?.addOnLayoutChangeListener(windowLayoutListener)
        binding.headerPlayback.setOnClickListener { carousel?.togglePause() }
        binding.headerExpand.setOnClickListener {
            val index = carousel?.prepareViewer() ?: return@setOnClickListener
            startActivity(Intent(requireContext(), HeaderImageActivity::class.java)
                .putExtra(HeaderImageActivity.EXTRA_IMAGE, images[index]))
        }
        updatePlayback()
        val themePreferenceStore = ThemePreferenceStore(requireContext())
        refreshPresentation(themePreferenceStore)
        binding.historyButton.setOnClickListener { startActivity(Intent(requireContext(), HistoryActivity::class.java)) }
        binding.collectionButton.setOnClickListener { startActivity(Intent(requireContext(), CollectionActivity::class.java)) }
        binding.hotButton.setOnClickListener { startActivity(Intent(requireContext(), HotActivity::class.java)) }
        binding.localMediaButton.setOnClickListener { startActivity(Intent(requireContext(), LocalMediaActivity::class.java)) }
        binding.themeSettingsButton.setOnClickListener {
            val modes = ThemeMode.entries
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.theme_settings_title)
                .setSingleChoiceItems(
                    modes.map { getString(it.labelRes) }.toTypedArray(),
                    modes.indexOf(themePreferenceStore.currentMode()),
                ) { dialog, which ->
                    dialog.dismiss()
                    themePreferenceStore.select(modes[which])
                }
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            configureExpansion()
            refreshPresentation(ThemePreferenceStore(requireContext()))
            binding.headerImage.doOnLayout { if (_binding != null && isResumed) carousel?.start() }
        }
    }

    private fun refreshPresentation(themePreferenceStore: ThemePreferenceStore) {
        binding.historyCount.text = AppGraph.catalogRepository.getHistory().size.toString()
        binding.favoriteCount.text = AppGraph.catalogRepository.getFavorites().size.toString()
        binding.themeSettingsSummary.setText(themePreferenceStore.currentMode().labelRes)
    }

    private fun configureExpansion(reload: Boolean = true, forceReload: Boolean = false) {
        val currentBinding = _binding ?: return
        val windowHeight = windowRoot?.height?.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        val changed = currentBinding.mineHeader.configureSize(windowHeight, topInset)
        if (reload && (changed || forceReload)) {
            // Only a window change reloads the two bounded targets, never a drag.
            carousel?.stop()
            currentBinding.headerImage.doOnLayout {
                if (_binding === currentBinding && isResumed) carousel?.start()
            }
        }
    }

    override fun onPause() {
        _binding?.mineHeader?.cancelInteraction()
        carousel?.stop()
        super.onPause()
    }

    private fun updatePlayback() {
        _binding?.let {
            it.headerPlayback.setImageResource(if (slideshow.paused) R.drawable.ic_play else R.drawable.ic_pause)
            it.headerPlayback.contentDescription = getString(
                if (slideshow.paused) R.string.mine_resume_slideshow else R.string.mine_pause_slideshow,
            )
            it.headerPlayback.isEnabled = !slideshow.exhausted
            it.headerExpand.isEnabled = carousel?.canExpand == true
        }
    }

    override fun onDestroyView() {
        windowRoot?.removeOnLayoutChangeListener(windowLayoutListener)
        windowRoot = null
        _binding?.mineHeader?.cancelInteraction()
        _binding?.mineHeader?.listener = null
        _binding?.mineHeader?.scrollPageTowardTop = null
        carousel?.dispose()
        carousel = null
        _binding = null
        super.onDestroyView()
    }

    private val ThemeMode.labelRes: Int
        get() = when (this) {
            ThemeMode.SYSTEM -> R.string.theme_mode_system
            ThemeMode.LIGHT -> R.string.theme_mode_light
            ThemeMode.DARK -> R.string.theme_mode_dark
        }
}

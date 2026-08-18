package com.aiyifan.app.feature.localmedia

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.aiyifan.app.R
import com.aiyifan.app.databinding.FragmentLocalMediaBinding
import com.aiyifan.app.feature.localmedia.data.AndroidLocalMediaRepository
import com.aiyifan.app.feature.localmedia.data.LocalMediaRepository
import com.aiyifan.app.feature.localmedia.data.LocalPlaybackStore
import com.aiyifan.app.feature.localmedia.model.LocalVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LocalMediaFragment : Fragment() {
    private var _binding: FragmentLocalMediaBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: LocalMediaRepository
    private lateinit var playbackStore: LocalPlaybackStore
    private lateinit var adapter: LocalMediaAdapter

    private var allVideos: List<LocalVideo> = emptyList()
    private var currentFilter: Filter = Filter.ALL
    private var lastKnownPermissionGranted: Boolean? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        lastKnownPermissionGranted = granted
        if (granted) loadVideos()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        repository = AndroidLocalMediaRepository(context.applicationContext)
        playbackStore = LocalPlaybackStore(context.applicationContext)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLocalMediaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = LocalMediaAdapter { video ->
            startActivity(LocalVideoPlayerActivity.intent(requireContext(), video))
        }
        binding.localRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.localRecycler.adapter = adapter

        binding.localPermissionButton.setOnClickListener { requestPermission() }
        binding.localSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                applySearch()
                true
            } else {
                false
            }
        }
        binding.localTabs.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                currentFilter = Filter.entries[tab.position]
                render()
            }

            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) = Unit
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) = Unit
        })

        syncPermissionState()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStateIfChanged()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun requestPermission() {
        val permission = LocalMediaPermissionPolicy.permissionFor(Build.VERSION.SDK_INT)
        if (ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED) {
            loadVideos()
        } else {
            permissionLauncher.launch(permission)
        }
    }

    private fun hasPermission(): Boolean {
        val permission = LocalMediaPermissionPolicy.permissionFor(Build.VERSION.SDK_INT)
        return ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun syncPermissionState() {
        val granted = hasPermission()
        lastKnownPermissionGranted = granted
        if (granted) loadVideos() else showPermissionState()
    }

    private fun refreshPermissionStateIfChanged() {
        val granted = hasPermission()
        if (granted == lastKnownPermissionGranted) return
        syncPermissionState()
    }

    private fun loadVideos() {
        if (!hasPermission()) {
            showPermissionState()
            return
        }

        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) { repository.queryVideos() }
            allVideos = loaded
            val availableIds = loaded.mapTo(mutableSetOf()) { it.id }
            withContext(Dispatchers.IO) { playbackStore.prune(availableIds) }
            render()
        }
    }

    private fun applySearch() {
        val query = binding.localSearch.text?.toString()?.trim().orEmpty()
        val base = when (currentFilter) {
            Filter.ALL -> allVideos
            Filter.RECENT_ADDED -> allVideos.sortedByDescending { it.dateAddedMs }
            Filter.RECENT_PLAYED -> recentPlayed()
        }
        val filtered = if (query.isEmpty()) base else LocalMediaLibraryPolicy.search(base, query)
        showVideos(filtered)
    }

    private fun render() {
        applySearch()
    }

    private fun recentPlayed(): List<LocalVideo> {
        val records = playbackStore.load()
        return records.mapNotNull { record ->
            allVideos.firstOrNull { it.id == record.mediaStoreId }
        }
    }

    private fun showVideos(videos: List<LocalVideo>) {
        adapter.submitList(videos)
        binding.localPermissionState.visibility = View.GONE
        binding.localRecycler.visibility = if (videos.isEmpty()) View.GONE else View.VISIBLE
        binding.localEmptyState.visibility = if (videos.isEmpty()) View.VISIBLE else View.GONE
        binding.localEmptyText.setText(if (allVideos.isEmpty()) R.string.local_media_empty else R.string.local_media_empty)
    }

    private fun showPermissionState() {
        binding.localRecycler.visibility = View.GONE
        binding.localEmptyState.visibility = View.GONE
        binding.localPermissionState.visibility = View.VISIBLE
    }

    private enum class Filter {
        ALL,
        RECENT_ADDED,
        RECENT_PLAYED,
    }
}

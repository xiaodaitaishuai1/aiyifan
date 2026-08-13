package com.aiyifan.app.feature.hot

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.aiyifan.app.core.data.AppGraph
import com.aiyifan.app.core.ui.VideoListAdapter
import com.aiyifan.app.databinding.FragmentHotBinding
import com.aiyifan.app.feature.proxy.ProxyConnectionObserver
import com.aiyifan.app.feature.video.VideoPlayerActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class HotFragment : Fragment() {
    private var _binding: FragmentHotBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: VideoListAdapter
    private var hotRequestVersion = 0L
    private val proxyConnectionObserver = ProxyConnectionObserver {
        if (_binding != null) {
            viewLifecycleOwner.lifecycleScope.launch { loadHot() }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHotBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = VideoListAdapter { video ->
            startActivity(VideoPlayerActivity.intent(requireContext(), video.mediaKey))
        }
        binding.hotRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.hotRecycler.adapter = adapter
        loadHot()
    }

    override fun onStart() {
        super.onStart()
        AppGraph.proxyManager.addConnectionObserver(proxyConnectionObserver)
    }

    override fun onStop() {
        AppGraph.proxyManager.removeConnectionObserver(proxyConnectionObserver)
        super.onStop()
    }

    private fun loadHot() {
        val requestVersion = ++hotRequestVersion
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { AppGraph.catalogRepository.getHotVideos() }
                .onSuccess { videos ->
                    if (requestVersion == hotRequestVersion) adapter.submitList(videos)
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    if (requestVersion == hotRequestVersion) {
                        Toast.makeText(requireContext(), "热门数据加载失败", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

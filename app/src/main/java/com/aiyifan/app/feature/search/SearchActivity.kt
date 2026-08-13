package com.aiyifan.app.feature.search

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.aiyifan.app.R
import com.aiyifan.app.core.data.AppGraph
import com.aiyifan.app.core.model.VideoSummary
import com.aiyifan.app.core.ui.SearchPageState
import com.aiyifan.app.core.ui.SearchResultAdapter
import com.aiyifan.app.core.ui.SearchSuggestionAdapter
import com.aiyifan.app.core.ui.applySystemBarsPadding
import com.aiyifan.app.core.ui.setupEdgeToEdge
import com.aiyifan.app.databinding.ActivitySearchBinding
import com.aiyifan.app.feature.video.VideoPlayerActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray

class SearchActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySearchBinding
    private lateinit var resultAdapter: SearchResultAdapter
    private lateinit var suggestionAdapter: SearchSuggestionAdapter
    private val historyPrefs by lazy { getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    private var suggestionJob: Job? = null
    private var historyExpanded = false
    private var lastSearchKeyword = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupEdgeToEdge()
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsPadding(left = true, right = true, bottom = true)
        binding.topBar.applySystemBarsPadding(top = true, growHeight = true)

        setupLists()
        setupInteractions()
        renderInitialState()
    }

    private fun setupLists() {
        resultAdapter = SearchResultAdapter { video ->
            startActivity(VideoPlayerActivity.intent(this, video.mediaKey))
        }
        binding.resultRecycler.layoutManager = LinearLayoutManager(this)
        binding.resultRecycler.adapter = resultAdapter

        suggestionAdapter = SearchSuggestionAdapter { suggestion ->
            binding.searchEdit.setText(suggestion.keyword)
            binding.searchEdit.setSelection(suggestion.keyword.length)
            executeSearch()
        }
        binding.suggestionRecycler.layoutManager = LinearLayoutManager(this)
        binding.suggestionRecycler.adapter = suggestionAdapter
    }

    private fun setupInteractions() {
        binding.backButton.setOnClickListener { finish() }
        binding.searchButton.setOnClickListener { executeSearch() }
        binding.searchStatus.setOnClickListener {
            if (binding.searchStatus.isClickable && lastSearchKeyword.isNotBlank()) executeSearch()
        }
        binding.searchEdit.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                executeSearch()
                true
            } else {
                false
            }
        }
        binding.searchEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(editable: Editable?) {
                requestSuggestions(editable?.toString().orEmpty().trim())
            }
        })
        binding.expandHistoryButton.setOnClickListener {
            historyExpanded = !historyExpanded
            renderHistory()
        }
        binding.clearHistoryButton.setOnClickListener { confirmClearHistory() }
    }

    private fun requestSuggestions(keyword: String) {
        suggestionJob?.cancel()
        if (keyword.isBlank()) {
            renderInitialState()
            return
        }
        binding.initialScroll.isVisible = false
        binding.resultsContainer.isVisible = false
        binding.suggestionRecycler.isVisible = true
        suggestionAdapter.submitList(emptyList())
        suggestionJob = lifecycleScope.launch {
            delay(SUGGESTION_DELAY_MS)
            runCatching { AppGraph.catalogRepository.searchSuggestions(keyword) }
                .onSuccess { suggestions ->
                    if (binding.searchEdit.text.toString().trim() == keyword) {
                        suggestionAdapter.submitList(suggestions)
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    if (binding.searchEdit.text.toString().trim() == keyword) {
                        suggestionAdapter.submitList(emptyList())
                    }
                }
        }
    }

    private fun executeSearch() {
        val keyword = binding.searchEdit.text.toString().trim()
        if (keyword.isEmpty()) {
            binding.searchEdit.error = "请输入搜索内容"
            binding.searchEdit.requestFocus()
            return
        }
        suggestionJob?.cancel()
        lastSearchKeyword = keyword
        binding.initialScroll.isVisible = false
        binding.suggestionRecycler.isVisible = false
        renderSearchState(SearchPageState.Loading)
        lifecycleScope.launch {
            runCatching { AppGraph.catalogRepository.searchVideos(keyword) }
                .onSuccess { results ->
                    saveKeyword(keyword)
                    renderSearchState(
                        if (results.isEmpty()) SearchPageState.Empty else SearchPageState.Success,
                        results,
                    )
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    renderSearchState(SearchPageState.Failure)
                }
        }
    }

    private fun renderSearchState(state: SearchPageState, results: List<VideoSummary> = emptyList()) {
        binding.resultsContainer.isVisible = state.showResults
        binding.searchStatus.isVisible = state.showMessage
        binding.searchStatus.isClickable = state.showRetry
        binding.resultRecycler.isVisible = state != SearchPageState.Loading
        resultAdapter.submitList(results)
        binding.searchStatus.text = when (state) {
            SearchPageState.Loading -> "正在搜索..."
            SearchPageState.Success -> ""
            SearchPageState.Empty -> "没有找到相关视频"
            SearchPageState.Failure -> "搜索失败，点击重试"
        }
    }

    private fun renderInitialState() {
        binding.initialScroll.isVisible = true
        binding.suggestionRecycler.isVisible = false
        binding.resultsContainer.isVisible = false
        renderHistory()
        renderKeywords(binding.hotContainer, HOT_KEYWORDS)
    }

    private fun renderHistory() {
        val history = readHistory()
        val visibleHistory = if (historyExpanded) history else history.take(HISTORY_PREVIEW_SIZE)
        renderKeywords(binding.historyContainer, visibleHistory)
        binding.clearHistoryButton.isVisible = history.isNotEmpty()
        binding.expandHistoryButton.isVisible = history.size > HISTORY_PREVIEW_SIZE
        binding.expandHistoryButton.text = if (historyExpanded) "收起" else "展开"
    }

    private fun renderKeywords(container: ViewGroup, keywords: List<String>) {
        container.removeAllViews()
        keywords.forEach { keyword ->
            container.addView(TextView(this).apply {
                text = keyword
                setTextColor(getColor(R.color.text_primary))
                textSize = 14f
                setBackgroundResource(R.drawable.bg_chip)
                isClickable = true
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                setOnClickListener {
                    binding.searchEdit.setText(keyword)
                    binding.searchEdit.setSelection(keyword.length)
                    executeSearch()
                }
            })
        }
        if (keywords.isEmpty() && container == binding.historyContainer) {
            container.addView(TextView(this).apply {
                text = "暂无搜索历史"
                setTextColor(getColor(R.color.text_secondary))
                textSize = 14f
            })
        }
    }

    private fun readHistory(): List<String> {
        val stored = historyPrefs.getString(HISTORY_KEY, null)
        if (stored != null) {
            return runCatching {
                val values = JSONArray(stored)
                List(values.length()) { index -> values.optString(index).trim() }.filter { it.isNotBlank() }
            }.getOrDefault(emptyList())
        }
        val legacy = historyPrefs.getStringSet(LEGACY_HISTORY_KEY, emptySet()).orEmpty().toList()
        if (legacy.isNotEmpty()) writeHistory(legacy)
        return legacy
    }

    private fun saveKeyword(keyword: String) {
        writeHistory((listOf(keyword) + readHistory().filterNot { it.equals(keyword, ignoreCase = true) }).take(HISTORY_LIMIT))
    }

    private fun writeHistory(keywords: List<String>) {
        historyPrefs.edit()
            .putString(HISTORY_KEY, JSONArray(keywords).toString())
            .remove(LEGACY_HISTORY_KEY)
            .apply()
    }

    private fun confirmClearHistory() {
        AlertDialog.Builder(this)
            .setMessage("确定清空全部搜索历史吗？")
            .setNegativeButton("取消", null)
            .setPositiveButton("清空") { _, _ ->
                historyPrefs.edit().remove(HISTORY_KEY).remove(LEGACY_HISTORY_KEY).apply()
                historyExpanded = false
                renderHistory()
                Toast.makeText(this, "搜索历史已清空", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    override fun onDestroy() {
        suggestionJob?.cancel()
        super.onDestroy()
    }

    private companion object {
        const val PREFS_NAME = "search_history"
        const val HISTORY_KEY = "history_ordered"
        const val LEGACY_HISTORY_KEY = "keywords"
        const val HISTORY_LIMIT = 20
        const val HISTORY_PREVIEW_SIZE = 10
        const val SUGGESTION_DELAY_MS = 300L
        val HOT_KEYWORDS = listOf("迷城风云", "长河旧事", "星桥少年", "烟火地图", "电影", "动漫")
    }
}

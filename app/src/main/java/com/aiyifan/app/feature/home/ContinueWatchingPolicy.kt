package com.aiyifan.app.feature.home

import com.aiyifan.app.core.model.WatchHistory

object ContinueWatchingPolicy {
    fun visible(records: List<WatchHistory>, hiddenAt: Map<String, Long>): List<WatchHistory> = records
        .filter { it.updatedAt > (hiddenAt[it.mediaKey] ?: -1L) }
        .sortedByDescending { it.updatedAt }
        .distinctBy { it.mediaKey }
        .take(5)
}

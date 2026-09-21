package com.aiyifan.app.core.data

import com.aiyifan.app.core.model.WatchHistory

interface WatchHistoryStore {
    fun save(history: WatchHistory)
    fun getHistory(): List<WatchHistory>
    fun clear()
}

class InMemoryWatchHistoryStore : WatchHistoryStore {
    private val records = linkedMapOf<String, WatchHistory>()

    @Synchronized
    override fun save(history: WatchHistory) {
        if (!history.isValid()) return
        val previous = records[history.mediaKey]
        if (previous == null || history.updatedAt >= previous.updatedAt) {
            records[history.mediaKey] = history
        }
    }

    @Synchronized
    override fun getHistory(): List<WatchHistory> = records.values.sortedByDescending { it.updatedAt }

    @Synchronized
    override fun clear() {
        records.clear()
    }
}

internal fun WatchHistory.isValid(): Boolean =
    mediaKey.isNotBlank() && episodeKey.isNotBlank() &&
        progressMs >= 0 && durationMs >= 0 && updatedAt >= 0

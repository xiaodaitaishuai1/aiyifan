package com.aiyifan.app.core.data

import android.content.SharedPreferences
import com.aiyifan.app.core.model.WatchHistory

class SharedPreferencesWatchHistoryStore(
    private val preferences: SharedPreferences,
) : WatchHistoryStore {
    override fun save(history: WatchHistory) {
        if (!history.isValid()) return
        synchronized(preferences) {
            val records = InMemoryWatchHistoryStore()
            getHistory().forEach(records::save)
            records.save(history)
            preferences.edit().putString(HISTORY_KEY, WatchHistoryCodec.encode(records.getHistory())).apply()
        }
    }

    override fun getHistory(): List<WatchHistory> = synchronized(preferences) {
        val payload = try {
            preferences.getString(HISTORY_KEY, null)
        } catch (_: ClassCastException) {
            null
        }
        WatchHistoryCodec.decode(payload)
    }

    override fun clear() {
        synchronized(preferences) {
            preferences.edit().remove(HISTORY_KEY).apply()
        }
    }

    private companion object {
        const val HISTORY_KEY = "watch_history"
    }
}

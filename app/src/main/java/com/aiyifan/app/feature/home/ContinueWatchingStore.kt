package com.aiyifan.app.feature.home

import android.content.SharedPreferences
import com.aiyifan.app.core.model.WatchHistory
import org.json.JSONObject

class ContinueWatchingStore(private val preferences: SharedPreferences) {
    fun visible(records: List<WatchHistory>): List<WatchHistory> = ContinueWatchingPolicy.visible(records, read())

    fun hide(record: WatchHistory) {
        val values = read().toMutableMap().apply { put(record.mediaKey, record.updatedAt) }
        preferences.edit().putString(KEY, JSONObject(values as Map<*, *>).toString()).apply()
    }

    private fun read(): Map<String, Long> = runCatching {
        val json = JSONObject(preferences.getString(KEY, null) ?: "{}")
        json.keys().asSequence().mapNotNull { key ->
            (json.opt(key) as? Number)?.toLong()?.takeIf { it >= 0 }?.let { key to it }
        }.toMap()
    }.getOrDefault(emptyMap())

    private companion object { const val KEY = "hidden_records" }
}

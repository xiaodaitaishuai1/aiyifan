package com.aiyifan.app.feature.localmedia.data

import android.content.Context
import com.aiyifan.app.feature.localmedia.LocalPlaybackStorePolicy
import com.aiyifan.app.feature.localmedia.model.LocalPlaybackRecord
import org.json.JSONArray
import org.json.JSONObject

class LocalPlaybackStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): List<LocalPlaybackRecord> {
        val raw = preferences.getString(KEY_RECORDS, null) ?: return emptyList()
        return runCatching { parse(raw) }.getOrDefault(emptyList())
    }

    fun save(incoming: LocalPlaybackRecord) {
        val merged = LocalPlaybackStorePolicy.merge(load(), incoming)
        preferences.edit().putString(KEY_RECORDS, serialize(merged)).apply()
    }

    fun prune(availableIds: Set<Long>) {
        val pruned = LocalPlaybackStorePolicy.prune(load(), availableIds)
        preferences.edit().putString(KEY_RECORDS, serialize(pruned)).apply()
    }

    fun clear() {
        preferences.edit().remove(KEY_RECORDS).apply()
    }

    private fun parse(raw: String): List<LocalPlaybackRecord> {
        val array = JSONArray(raw)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    LocalPlaybackRecord(
                        mediaStoreId = item.getLong("mediaStoreId"),
                        contentUri = item.getString("contentUri"),
                        displayName = item.getString("displayName"),
                        durationMs = item.getLong("durationMs"),
                        positionMs = item.getLong("positionMs"),
                        updatedAt = item.getLong("updatedAt"),
                    ),
                )
            }
        }
    }

    private fun serialize(records: List<LocalPlaybackRecord>): String {
        val array = JSONArray()
        records.forEach { record ->
            array.put(
                JSONObject()
                    .put("mediaStoreId", record.mediaStoreId)
                    .put("contentUri", record.contentUri)
                    .put("displayName", record.displayName)
                    .put("durationMs", record.durationMs)
                    .put("positionMs", record.positionMs)
                    .put("updatedAt", record.updatedAt),
            )
        }
        return array.toString()
    }

    private companion object {
        const val PREFERENCES_NAME = "local_media_playback"
        const val KEY_RECORDS = "records"
    }
}
package com.aiyifan.app.core.data

import com.aiyifan.app.core.model.WatchHistory
import org.json.JSONArray
import org.json.JSONObject

internal object WatchHistoryCodec {
    fun encode(records: List<WatchHistory>): String {
        val items = JSONArray()
        records.filter { it.isValid() }.forEach { record ->
            items.put(
                JSONObject()
                    .put("mediaKey", record.mediaKey)
                    .put("episodeKey", record.episodeKey)
                    .put("title", record.title)
                    .put("coverUrl", record.coverUrl)
                    .put("videoType", record.videoType)
                    .put("progressMs", record.progressMs)
                    .put("durationMs", record.durationMs)
                    .put("updatedAt", record.updatedAt),
            )
        }
        return items.toString()
    }

    fun decode(payload: String?): List<WatchHistory> {
        if (payload.isNullOrBlank()) return emptyList()
        val items = runCatching { JSONArray(payload) }.getOrNull() ?: return emptyList()
        val store = InMemoryWatchHistoryStore()
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val record = decodeRecord(item) ?: continue
            store.save(record)
        }
        return store.getHistory()
    }

    private fun decodeRecord(item: JSONObject): WatchHistory? {
        val videoType = item.longValue("videoType") ?: return null
        if (videoType !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) return null
        return WatchHistory(
            mediaKey = item.opt("mediaKey") as? String ?: return null,
            episodeKey = item.opt("episodeKey") as? String ?: return null,
            title = item.opt("title") as? String ?: return null,
            coverUrl = item.opt("coverUrl") as? String ?: return null,
            videoType = videoType.toInt(),
            progressMs = item.longValue("progressMs") ?: return null,
            durationMs = item.longValue("durationMs") ?: return null,
            updatedAt = item.longValue("updatedAt") ?: return null,
        ).takeIf { it.isValid() }
    }

    private fun JSONObject.longValue(key: String): Long? = when (val value = opt(key)) {
        is Int -> value.toLong()
        is Long -> value
        else -> null
    }
}

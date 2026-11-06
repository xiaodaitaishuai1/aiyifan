package com.aiyifan.app.core.data

import com.aiyifan.app.core.model.FavoriteTracking
import com.aiyifan.app.core.model.FavoriteVideo
import com.aiyifan.app.core.model.VideoDetail
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

interface FavoriteStore {
    fun favorites(): List<FavoriteVideo>
    fun tracking(mediaKey: String): FavoriteTracking?
    fun generation(mediaKey: String): String?
    fun toggle(detail: VideoDetail, now: Long): Boolean
    fun remove(mediaKey: String)
    fun update(detail: VideoDetail, now: Long, generation: String)
    fun markKnown(detail: VideoDetail)
}

class InMemoryFavoriteStore(
    payload: String? = null,
    private val persist: (String) -> Unit = {},
) : FavoriteStore {
    private data class Entry(
        val favorite: FavoriteVideo,
        val tracking: FavoriteTracking?,
        val generation: String,
    )

    private val entries = linkedMapOf<String, Entry>()

    init {
        val array = runCatching { JSONArray(payload ?: "[]") }.getOrNull() ?: JSONArray()
        for (index in 0 until array.length()) {
            runCatching {
                val item = array.getJSONObject(index)
                val favorite = FavoriteVideo(
                    mediaKey = item.getString("mediaKey"),
                    title = item.getString("title"),
                    coverUrl = item.getString("coverUrl"),
                    videoType = item.getInt("videoType"),
                    createdAt = item.getLong("createdAt"),
                )
                require(favorite.mediaKey.isNotBlank() && favorite.createdAt >= 0)
                val snapshot = item.optJSONObject("tracking")?.let {
                    FavoriteTracking(
                        knownEpisodeKeys = it.getJSONArray("known").keys(),
                        latestEpisodeKeys = it.getJSONArray("latest").keys(),
                        latestEpisodeTitle = it.opt("latestTitle") as? String,
                        checkedAt = it.getLong("checkedAt"),
                    ).also { value -> require(value.checkedAt >= 0) }
                }
                entries[favorite.mediaKey] = Entry(favorite, snapshot, UUID.randomUUID().toString())
            }
        }
    }

    @Synchronized
    override fun favorites(): List<FavoriteVideo> =
        entries.values.map { it.favorite }.sortedByDescending { it.createdAt }

    @Synchronized
    override fun tracking(mediaKey: String): FavoriteTracking? = entries[mediaKey]?.tracking

    @Synchronized
    override fun generation(mediaKey: String): String? = entries[mediaKey]?.generation

    @Synchronized
    override fun toggle(detail: VideoDetail, now: Long): Boolean {
        require(detail.mediaKey.isNotBlank())
        if (entries.containsKey(detail.mediaKey)) {
            remove(detail.mediaKey)
            return false
        }
        val keys = detail.keys()
        entries[detail.mediaKey] = Entry(
            favorite = FavoriteVideo(detail.mediaKey, detail.title, detail.coverUrl, detail.videoType, now),
            tracking = FavoriteTracking(keys, keys, detail.episodes.lastOrNull()?.episodeTitle, now),
            generation = UUID.randomUUID().toString(),
        )
        save()
        return true
    }

    @Synchronized
    override fun remove(mediaKey: String) {
        if (entries.remove(mediaKey) != null) save()
    }

    @Synchronized
    override fun update(detail: VideoDetail, now: Long, generation: String) {
        val entry = entries[detail.mediaKey] ?: return
        if (entry.generation != generation) return
        val keys = detail.keys()
        entries[detail.mediaKey] = entry.copy(
            tracking = FavoriteTracking(
                knownEpisodeKeys = entry.tracking?.knownEpisodeKeys ?: keys,
                latestEpisodeKeys = keys,
                latestEpisodeTitle = detail.episodes.lastOrNull()?.episodeTitle,
                checkedAt = now,
            ),
        )
        save()
    }

    @Synchronized
    override fun markKnown(detail: VideoDetail) {
        val entry = entries[detail.mediaKey] ?: return
        val snapshot = entry.tracking ?: return
        entries[detail.mediaKey] = entry.copy(
            tracking = snapshot.copy(knownEpisodeKeys = snapshot.knownEpisodeKeys + detail.keys()),
        )
        save()
    }

    private fun save() {
        val array = JSONArray()
        entries.values.forEach { entry ->
            val item = JSONObject()
                .put("mediaKey", entry.favorite.mediaKey)
                .put("title", entry.favorite.title)
                .put("coverUrl", entry.favorite.coverUrl)
                .put("videoType", entry.favorite.videoType)
                .put("createdAt", entry.favorite.createdAt)
            entry.tracking?.let {
                item.put(
                    "tracking",
                    JSONObject()
                        .put("known", JSONArray(it.knownEpisodeKeys.toList()))
                        .put("latest", JSONArray(it.latestEpisodeKeys.toList()))
                        .put("latestTitle", it.latestEpisodeTitle)
                        .put("checkedAt", it.checkedAt),
                )
            }
            array.put(item)
        }
        persist(array.toString())
    }

    private fun VideoDetail.keys(): Set<String> =
        episodes.map { it.episodeKey }.filter { it.isNotBlank() }.toSet()

    private fun JSONArray.keys(): Set<String> =
        (0 until length()).map { getString(it) }.filter { it.isNotBlank() }.toSet()
}

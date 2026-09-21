package com.aiyifan.app.core.data

import android.content.SharedPreferences
import com.aiyifan.app.core.data.remote.RemoteCatalogRepository
import com.aiyifan.app.core.model.Episode
import com.aiyifan.app.core.model.VideoDetail
import com.aiyifan.app.core.model.WatchHistory
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class WatchHistoryStoreTest {
    @Test
    fun `memory store keeps latest record per media and sorts by update time`() {
        val store = InMemoryWatchHistoryStore()
        store.save(history("first", updatedAt = 10))
        store.save(history("second", updatedAt = 20))
        store.save(history("first", updatedAt = 30).copy(episodeKey = "episode-2", progressMs = 900))
        store.save(history("first", updatedAt = 5))

        assertEquals(listOf("first", "second"), store.getHistory().map { it.mediaKey })
        assertEquals("episode-2", store.getHistory().first().episodeKey)
        assertEquals(900L, store.getHistory().first().progressMs)
    }

    @Test
    fun `invalid save cannot replace a valid history record`() {
        val store = InMemoryWatchHistoryStore()
        val valid = history()
        store.save(valid)
        store.save(valid.copy(progressMs = -1))
        store.save(valid.copy(episodeKey = ""))
        store.save(valid.copy(mediaKey = " "))

        assertEquals(listOf(valid), store.getHistory())
    }

    @Test
    fun `clear removes all memory history`() {
        val store = InMemoryWatchHistoryStore()
        store.save(history())
        store.clear()
        assertTrue(store.getHistory().isEmpty())
    }

    @Test
    fun `json codec preserves all history fields including unicode and long positions`() {
        val original = history().copy(title = "剧集 \"第一季\"", progressMs = 3_000_000_000L, durationMs = 4_000_000_000L)

        assertEquals(listOf(original), WatchHistoryCodec.decode(WatchHistoryCodec.encode(listOf(original))))
    }

    @Test
    fun `json codec returns empty for absent or corrupt payload`() {
        listOf(null, "", "broken json", "{}", "null").forEach { payload ->
            assertTrue(WatchHistoryCodec.decode(payload).isEmpty())
        }
    }

    @Test
    fun `json codec skips malformed records while retaining valid entries`() {
        val valid = history()
        val validJson = JSONArray(WatchHistoryCodec.encode(listOf(valid))).getJSONObject(0)
        val payload = JSONArray()
            .put(JSONObject(validJson.toString()).put("mediaKey", " "))
            .put(JSONObject(validJson.toString()).put("durationMs", -1))
            .put(JSONObject(validJson.toString()).put("updatedAt", "invalid"))
            .put(JSONObject(validJson.toString()).put("progressMs", JSONObject.NULL))
            .put(JSONObject(validJson.toString()).put("progressMs", 1.5))
            .put(JSONObject(validJson.toString()).put("title", JSONObject.NULL))
            .put(JSONObject())
            .put("not an object")
            .put(validJson)

        assertEquals(listOf(valid), WatchHistoryCodec.decode(payload.toString()))
    }

    @Test
    fun `json codec deduplicates and orders restored records`() {
        val latest = history("one", 30)
        val other = history("two", 20)
        val payload = JSONArray()
        listOf(history("one", 10), other, latest).forEach { record ->
            payload.put(JSONArray(WatchHistoryCodec.encode(listOf(record))).getJSONObject(0))
        }

        assertEquals(listOf(latest, other), WatchHistoryCodec.decode(payload.toString()))
    }

    @Test
    fun `preferences store restores saved records from a fresh instance and persists clear`() {
        val preferences = memoryPreferences()
        val first = SharedPreferencesWatchHistoryStore(preferences)
        val record = history()
        first.save(record)

        val restored = SharedPreferencesWatchHistoryStore(preferences)
        assertEquals(listOf(record), restored.getHistory())
        restored.clear()
        assertTrue(SharedPreferencesWatchHistoryStore(preferences).getHistory().isEmpty())
    }

    @Test
    fun `remote repository history is shared through its injected store`() {
        val store = InMemoryWatchHistoryStore()
        val repository = RemoteCatalogRepository(historyStore = store, clock = { 123L })
        val detail = VideoDetail("video", "Title", "cover", 1)
        val episode = Episode("episode", "1", 1, null)
        repository.saveHistory(detail, episode, 400, 1000)

        assertEquals(listOf(WatchHistory("video", "episode", "Title", "cover", 1, 400, 1000, 123)), store.getHistory())
        assertEquals(store.getHistory(), RemoteCatalogRepository(historyStore = store).getHistory())
        repository.clearHistory()
        assertTrue(store.getHistory().isEmpty())
    }

    private fun history(mediaKey: String = "video", updatedAt: Long = 100L) = WatchHistory(
        mediaKey = mediaKey,
        episodeKey = "episode-1",
        title = "Title",
        coverUrl = "https://example.com/cover.jpg",
        videoType = 1,
        progressMs = 200,
        durationMs = 1000,
        updatedAt = updatedAt,
    )

    private fun memoryPreferences(): SharedPreferences {
        val values = mutableMapOf<String, String?>()
        val pending = mutableMapOf<String, String?>()
        val editor = Proxy.newProxyInstance(
            SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "putString" -> proxy.also { pending[args[0] as String] = args[1] as String? }
                "remove" -> proxy.also { pending[args[0] as String] = null }
                "apply", "commit" -> true.also {
                    pending.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
                    pending.clear()
                }
                else -> error("Unexpected preferences editor method: ${method.name}")
            }
        } as SharedPreferences.Editor
        return Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getString" -> values[args[0] as String] ?: args[1]
                "edit" -> editor
                else -> error("Unexpected preferences method: ${method.name}")
            }
        } as SharedPreferences
    }
}

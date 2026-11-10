package com.aiyifan.app.core.data.remote

import com.aiyifan.app.core.data.HomeVideoPage
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Process-local cache. Only successful responses are retained, for at most five minutes. */
internal class CatalogPageCache(private val clock: () -> Long) {
    private data class Key(val group: String, val page: String)
    private data class Entry(val value: HomeVideoPage, val savedAt: Long)
    private val entries = LinkedHashMap<Key, Entry>(16, 0.75f, true)
    private val mutex = Mutex()

    suspend fun get(
        group: String,
        page: String,
        refresh: Boolean = false,
        loader: suspend () -> HomeVideoPage,
    ): HomeVideoPage = mutex.withLock {
        val key = Key(group, page)
        val cached = entries[key]
        if (!refresh && cached != null && clock() - cached.savedAt in 0 until 300_000L) {
            return@withLock cached.value
        }
        val value = loader()
        currentCoroutineContext().ensureActive()
        // Refresh invalidates later pages only after the replacement succeeds.
        if (refresh) entries.keys.removeAll { it.group == group }
        entries[key] = Entry(value, clock())
        while (entries.size > 64) entries.remove(entries.keys.first())
        value
    }
}

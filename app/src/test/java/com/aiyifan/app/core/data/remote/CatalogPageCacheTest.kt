package com.aiyifan.app.core.data.remote

import com.aiyifan.app.core.data.HomeVideoPage
import com.aiyifan.app.core.model.VideoSummary
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CatalogPageCacheTest {
    private fun page(id: String) = HomeVideoPage(listOf(VideoSummary(id, title = id, coverUrl = "", videoType = 0)), true)

    @Test fun `reuse successful pages until expiry and isolate page keys`() = runBlocking {
        var now = 0L
        var calls = 0
        val cache = CatalogPageCache { now }
        suspend fun get(key: String) = cache.get("home:3", key) { page((++calls).toString()) }
        assertEquals(get("1"), get("1"))
        get("2")
        assertEquals(2, calls)
        now = 300_000
        get("1")
        assertEquals(3, calls)
    }

    @Test fun `refresh replaces first page and evicts older pages only in its group`() = runBlocking {
        val cache = CatalogPageCache { 0 }
        cache.get("home:3", "1") { page("old") }
        cache.get("home:3", "2") { page("old2") }
        cache.get("drama", "1") { page("drama") }
        cache.get("home:3", "1", refresh = true) { page("new") }
        assertEquals(page("new"), cache.get("home:3", "1") { error("must use cache") })
        assertEquals(page("new2"), cache.get("home:3", "2") { page("new2") })
        assertEquals(page("drama"), cache.get("drama", "1") { error("must use cache") })
    }

    @Test fun `failure is not cached and failed refresh preserves previous success`() = runBlocking {
        val cache = CatalogPageCache { 0 }
        assertTrue(runCatching { cache.get("drama", "1") { error("offline") } }.isFailure)
        cache.get("drama", "1") { page("good") }
        assertTrue(runCatching { cache.get("drama", "1", refresh = true) { error("offline") } }.isFailure)
        assertEquals(page("good"), cache.get("drama", "1") { error("must use cache") })
    }

    @Test fun `concurrent identical loads make one request`() = runBlocking {
        val cache = CatalogPageCache { 0 }
        var calls = 0
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val first = async { cache.get("drama", "1") { calls++; entered.complete(Unit); release.await(); page("one") } }
        entered.await()
        val second = async { cache.get("drama", "1") { calls++; page("two") } }
        release.complete(Unit)
        assertEquals(first.await(), second.await())
        assertEquals(1, calls)
    }
}

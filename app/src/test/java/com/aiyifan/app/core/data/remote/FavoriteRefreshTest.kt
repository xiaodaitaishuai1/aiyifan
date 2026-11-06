package com.aiyifan.app.core.data.remote

import com.aiyifan.app.core.model.VideoDetail
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FavoriteRefreshTest {
    @Test fun `strict refresh bypasses cache and failure preserves badge`() = runBlocking {
        val fetcher = Fetcher()
        val repo = RemoteCatalogRepository(configResolver = RemoteConfigResolver(fetcher), fetcher = fetcher)
        val first = repo.getVideoDetail("m")
        repo.toggleFavorite(first)
        fetcher.keys = listOf("a", "b")
        repo.checkFavoriteUpdates("m", true)
        assertTrue(repo.getFavoriteTracking("m")!!.hasUpdates)
        assertEquals(2, repo.getVideoDetail("m").episodes.size)
        val snapshot = repo.getFavoriteTracking("m")
        fetcher.failure = IllegalStateException("offline")
        try { repo.checkFavoriteUpdates("m", true); fail("Expected failure") } catch (_: IllegalStateException) { }
        assertEquals(snapshot, repo.getFavoriteTracking("m"))
        fetcher.failure = CancellationException("cancel")
        try { repo.refreshVideoDetail("m"); fail("Expected cancellation") } catch (_: CancellationException) { }
        assertEquals(snapshot, repo.getFavoriteTracking("m"))
    }

    @Test fun `fallback detail cannot acknowledge real update`() = runBlocking {
        val fetcher = Fetcher()
        val repo = RemoteCatalogRepository(configResolver = RemoteConfigResolver(fetcher), fetcher = fetcher)
        repo.toggleFavorite(repo.getVideoDetail("m"))
        fetcher.keys = listOf("a", "b")
        repo.checkFavoriteUpdates("m", true)
        repo.markFavoriteEpisodesKnown(VideoDetail("m", "fake", "", 1))
        assertTrue(repo.getFavoriteTracking("m")!!.hasUpdates)
        repo.markFavoriteEpisodesKnown(repo.getVideoDetail("m"))
        assertFalse(repo.getFavoriteTracking("m")!!.hasUpdates)
    }

    @Test fun `malformed episode payload never clears existing update snapshot`() = runBlocking {
        val fetcher = Fetcher()
        val repo = RemoteCatalogRepository(configResolver = RemoteConfigResolver(fetcher), fetcher = fetcher)
        repo.toggleFavorite(repo.getVideoDetail("m"))
        fetcher.keys = listOf("a", "b")
        repo.checkFavoriteUpdates("m", true)
        val previous = repo.getFavoriteTracking("m")
        listOf("", "\"episodes\":{},", "\"episodes\":[{}],", "\"episodes\":[{\"episodeKey\":\" \"}],").forEach { field ->
            fetcher.payload = """{"data":{"detailInfo":{$field"mediaKey":"m","title":"Title"}}}"""
            try {
                repo.checkFavoriteUpdates("m", true)
                fail("Malformed episode payload must fail")
            } catch (_: IllegalStateException) { }
            assertEquals(previous, repo.getFavoriteTracking("m"))
        }
    }

    @Test
    fun `automatic checks honor freshness while forced checks bypass interval`() = runBlocking {
        var now = 1_000L
        val fetcher = Fetcher()
        val repo = RemoteCatalogRepository(
            configResolver = RemoteConfigResolver(fetcher),
            fetcher = fetcher,
            clock = { now },
        )
        repo.toggleFavorite(repo.getVideoDetail("m"))
        assertEquals(1, fetcher.detailRequests)

        now += 15 * 60_000L - 1
        repo.checkFavoriteUpdates("m")
        assertEquals(1, fetcher.detailRequests)
        assertEquals(1_000L, repo.getFavoriteTracking("m")!!.checkedAt)

        repo.checkFavoriteUpdates("m", force = true)
        assertEquals(2, fetcher.detailRequests)
        assertEquals(now, repo.getFavoriteTracking("m")!!.checkedAt)
        repo.checkFavoriteUpdates("m")
        assertEquals(2, fetcher.detailRequests)

        now += 15 * 60_000L + 1
        repo.checkFavoriteUpdates("m")
        assertEquals(3, fetcher.detailRequests)
        assertEquals(now, repo.getFavoriteTracking("m")!!.checkedAt)
    }

    @Test
    fun `canceled suspended check preserves snapshot and real detail cache`() = runBlocking {
        withTimeout(5_000) {
            val fetcher = Fetcher()
            val repo = RemoteCatalogRepository(configResolver = RemoteConfigResolver(fetcher), fetcher = fetcher)
            repo.toggleFavorite(repo.getVideoDetail("m"))
            fetcher.keys = listOf("a", "b")
            repo.checkFavoriteUpdates("m", force = true)
            val snapshot = repo.getFavoriteTracking("m")
            val cachedDetail = repo.getVideoDetail("m")
            val gate = FetchGate()
            fetcher.gate = gate
            fetcher.keys = listOf("a", "b", "c")

            val check = launch { repo.checkFavoriteUpdates("m", force = true) }
            gate.entered.await()
            check.cancel()
            gate.release.complete(Unit)
            check.join()

            assertTrue(check.isCancelled)
            assertEquals(snapshot, repo.getFavoriteTracking("m"))
            assertTrue(repo.getFavoriteTracking("m")!!.hasUpdates)
            assertTrue(cachedDetail === repo.getVideoDetail("m"))
        }
    }

    @Test
    fun `suspended check cannot update a removed then readded favorite`() = runBlocking {
        withTimeout(5_000) {
            var now = 10L
            val fetcher = Fetcher()
            val repo = RemoteCatalogRepository(
                configResolver = RemoteConfigResolver(fetcher),
                fetcher = fetcher,
                clock = { now },
            )
            val baselineDetail = repo.getVideoDetail("m")
            repo.toggleFavorite(baselineDetail)
            val gate = FetchGate()
            fetcher.gate = gate
            fetcher.keys = listOf("a", "b")
            val check = launch { repo.checkFavoriteUpdates("m", force = true) }
            gate.entered.await()

            repo.removeFavorite("m")
            now = 20L
            repo.toggleFavorite(baselineDetail)
            val readdedSnapshot = repo.getFavoriteTracking("m")
            now = 30L
            gate.release.complete(Unit)
            check.join()

            assertTrue(repo.isFavorite("m"))
            assertEquals(20L, repo.getFavorites().single().createdAt)
            assertEquals(readdedSnapshot, repo.getFavoriteTracking("m"))
            assertFalse(repo.getFavoriteTracking("m")!!.hasUpdates)
        }
    }

    private class FetchGate {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
    }

    private class Fetcher : HttpFetcher {
        var gate: FetchGate? = null
        var detailRequests = 0
        var payload: String? = null
        var keys = listOf("a")
        var failure: Throwable? = null
        override suspend fun get(url: String): HttpResponse {
            if (url.endsWith("rawApp.json")) return HttpResponse(200, """{"api":"https://catalog.example"}""")
            detailRequests++
            gate?.let {
                it.entered.complete(Unit)
                // Model a transport returning after cancellation, as a blocking IO call can do.
                withContext(NonCancellable) { it.release.await() }
            }
            failure?.let { throw it }
            payload?.let { return HttpResponse(200, it) }
            val episodes = keys.joinToString(",") { """{"episodeKey":"$it","episodeTitle":"$it","episodeId":1}""" }
            return HttpResponse(200, """{"data":{"detailInfo":{"mediaKey":"m","title":"Title","videoType":1,"episodes":[$episodes]}}}""")
        }
    }
}

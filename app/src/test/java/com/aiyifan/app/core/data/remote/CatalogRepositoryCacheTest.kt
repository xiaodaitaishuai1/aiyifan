package com.aiyifan.app.core.data.remote

import com.aiyifan.app.core.model.Category
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CatalogRepositoryCacheTest {
    private class Fetcher : HttpFetcher {
        var requests = 0
        var status = 200
        override suspend fun get(url: String) = HttpResponse(200, """{"api":"https://catalog.example"}""")
        override suspend fun postJson(url: String, body: String): HttpResponse {
            requests++
            val category = JSONObject(body).getString("titleid")
            return HttpResponse(status, """{"ret":200,"data":{"list":[
                {"type":$category,"name":"Category","pageCount":5,"list":[{"mediaKey":"item-$requests","title":"Title"}]}
            ]}}""")
        }
    }

    @Test fun `home and drama reuse responses while explicit refresh hits server`() = runBlocking {
        val fetcher = Fetcher()
        val repo = RemoteCatalogRepository(configResolver = RemoteConfigResolver(fetcher), fetcher = fetcher)
        val category = Category("3", "Category", 1, 0)
        val home = repo.getHomeVideoPage(category, 1)
        val drama = repo.getShortDramaPage(1)
        assertEquals(home, repo.getHomeVideoPage(category, 1))
        assertEquals(drama, repo.getShortDramaPage(1))
        assertEquals(2, fetcher.requests)
        assertNotEquals(home, repo.refreshHomeVideoPage(category))
        assertNotEquals(drama, repo.refreshShortDramaPage())
        assertEquals(4, fetcher.requests)
        repo.getHomeVideoPage(category, 2)
        repo.getHomeVideoPage(category, 1, 10)
        repo.getHomeVideoPage(category.copy(id = "4"), 1)
        assertEquals(7, fetcher.requests)
    }

    @Test fun `home fallback and failed refresh never poison success cache`() = runBlocking {
        val fetcher = Fetcher()
        val repo = RemoteCatalogRepository(configResolver = RemoteConfigResolver(fetcher), fetcher = fetcher)
        val category = Category("3", "Category", 1, 0)
        fetcher.status = 500
        repo.getHomeVideoPage(category, 1)
        fetcher.status = 200
        val good = repo.getHomeVideoPage(category, 1)
        assertEquals(2, fetcher.requests)
        fetcher.status = 500
        assertTrue(runCatching { repo.refreshHomeVideoPage(category) }.isFailure)
        assertEquals(good, repo.getHomeVideoPage(category, 1))
        assertEquals(3, fetcher.requests)
    }
}

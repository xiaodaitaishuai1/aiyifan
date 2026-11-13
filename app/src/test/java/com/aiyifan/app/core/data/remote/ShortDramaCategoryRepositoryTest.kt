package com.aiyifan.app.core.data.remote

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortDramaCategoryRepositoryTest {
    private class Fetcher : HttpFetcher {
        val requests = mutableListOf<Pair<String, String>>()
        override suspend fun get(url: String) = HttpResponse(200, "{\"api\":\"https://catalog.example\"}")
        override suspend fun postJson(url: String, body: String): HttpResponse {
            requests += url to body
            return when {
                url.contains("GetChannelNavigation") -> HttpResponse(200, """
                    {"ret":200,"data":{"list":[
                      {"categoryId":8,"name":"短剧","list":[{"classifyId":19,"classifyName":"喜剧","classType":1,"index":2}]},
                      {"categoryId":3,"name":"电影","list":[{"classifyId":20,"classifyName":"动作","classType":1,"index":1}]}
                    ]}}
                """.trimIndent())
                else -> HttpResponse(200, """
                    {"ret":200,"data":{"list":[{"mediaKey":"category-video","title":"分类视频","videoType":1}]}}
                """.trimIndent())
            }
        }
    }

    @Test
    fun `loads only channel eight categories and routes category page`() = runBlocking {
        val fetcher = Fetcher()
        val repo = RemoteCatalogRepository(configResolver = RemoteConfigResolver(fetcher), fetcher = fetcher)

        val categories = repo.getShortDramaCategories()
        val page = repo.getShortDramaPage(categories.single(), 1, 30)

        assertEquals(listOf("19"), categories.map { it.id })
        assertEquals(listOf("category-video"), page.videos.map { it.mediaKey })
        val request = fetcher.requests.last()
        assertTrue(request.first.endsWith("api/List/GetConditionFilterData"))
        assertEquals("8", JSONObject(request.second).getString("titleid"))
        assertEquals("19", JSONObject(request.second).getString("ids").split(",")[2])
    }
}

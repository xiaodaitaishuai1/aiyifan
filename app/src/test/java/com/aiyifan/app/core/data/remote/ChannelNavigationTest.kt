package com.aiyifan.app.core.data.remote

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class ChannelNavigationTest {
    private class Fetcher : HttpFetcher {
        var url = ""
        var body = ""
        var response = """{"ret":200,"data":{"list":[{"categoryId":3,"name":"电影","list":[{"classifyId":19,"classifyName":"喜剧","classType":1,"index":1}]},{"categoryId":"news","name":"新闻","list":[{"classifyId":0,"classifyName":"国际新闻","classType":2,"index":0,"subID":"国际"}]},{"categoryId":7,"name":"空频道","list":[]}]}}"""
        override suspend fun get(url: String) = HttpResponse(200, """{"api":"https://example.test/"}""")
        override suspend fun postJson(url: String, body: String): HttpResponse {
            this.url = url
            this.body = body
            return HttpResponse(200, response)
        }
    }
    @Test fun `channels preserve nested tags and skip empty channels`() = runBlocking {
        val f = Fetcher()
        val repo = ChannelNavigationRepository(f, RemoteConfigResolver(f))
        val channels = repo.loadChannels()
        assertTrue(f.url.endsWith("api/Home/GetChannelNavigation"))
        assertEquals(listOf("电影", "新闻"), channels.map { it.name })
        assertEquals("喜剧", channels[0].tags.single().name)
        assertEquals("国际", channels[1].tags.single().subId)
    }
    @Test fun `filter index determines the ids slot and short channel uses sub ID`() = runBlocking {
        val f = Fetcher()
        val repo = ChannelNavigationRepository(f, RemoteConfigResolver(f))
        val channels = repo.loadChannels()
        f.response = """{"ret":200,"data":{"list":[]}}"""
        repo.loadVideos(channels[0].id, channels[0].tags[0], 1)
        assertTrue(f.url.endsWith("api/List/GetConditionFilterData"))
        assertEquals("0,19,0,0,0,0", JSONObject(f.body).getString("ids"))
        repo.loadVideos(channels[1].id, channels[1].tags[0], 2)
        assertTrue(f.url.endsWith("api/List/GetSmallVideo?videoType=3"))
        assertEquals("国际", JSONObject(f.body).getString("name"))
        assertEquals("2", JSONObject(f.body).getString("page"))
    }
    @Test fun `service errors do not become fallback channels`() = runBlocking {
        val f = Fetcher().apply { response = """{"ret":500,"data":{"list":[]}}""" }
        assertTrue(runCatching { ChannelNavigationRepository(f, RemoteConfigResolver(f)).loadChannels() }.isFailure)
    }
}

package com.aiyifan.app.core.data.remote

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class ChannelNavigationTest {
    private class Fetcher : HttpFetcher {
        var requests = 0
        var beforeResponse: suspend () -> Unit = {}
        var url = ""
        var body = ""
        var response = """{"ret":200,"data":{"list":[{"categoryId":3,"name":"电影","list":[{"classifyId":19,"classifyName":"喜剧","classType":1,"index":1}]},{"categoryId":"news","name":"新闻","list":[{"classifyId":0,"classifyName":"国际新闻","classType":2,"index":0,"subID":"国际"}]},{"categoryId":7,"name":"空频道","list":[]}]}}"""
        override suspend fun get(url: String) = HttpResponse(200, """{"api":"https://example.test/"}""")
        override suspend fun postJson(url: String, body: String): HttpResponse {
            requests++
            beforeResponse()
            this.url = url
            this.body = body
            return HttpResponse(200, response)
        }
    }
    @Test fun `reopening navigation reuses successful channel response`() = runBlocking {
        val f = Fetcher()
        val repo = ChannelNavigationRepository(f, RemoteConfigResolver(f))
        val first = repo.loadChannels()
        assertEquals(first, repo.loadChannels())
        assertEquals(1, f.requests)
    }
    @Test fun `channel cache expires after five minutes and rejects backwards clock`() = runBlocking {
        val f = Fetcher()
        var now = 0L
        val repo = ChannelNavigationRepository(f, RemoteConfigResolver(f), clock = { now })
        repo.loadChannels()
        now = 299_999L
        repo.loadChannels()
        assertEquals(1, f.requests)
        now = 300_000L
        repo.loadChannels()
        assertEquals(2, f.requests)
        now--
        repo.loadChannels()
        assertEquals(3, f.requests)
    }

    @Test fun `failed and empty channel responses remain retryable`() = runBlocking {
        val f = Fetcher()
        val success = f.response
        val repo = ChannelNavigationRepository(f, RemoteConfigResolver(f))
        f.response = """{"ret":500,"data":{"list":[]}}"""
        assertTrue(runCatching { repo.loadChannels() }.isFailure)
        f.response = """{"ret":200,"data":{"list":[]}}"""
        assertTrue(repo.loadChannels().isEmpty())
        f.response = success
        assertEquals(2, repo.loadChannels().size)
        repo.loadChannels()
        assertEquals(3, f.requests)
    }

    @Test fun `simultaneous channel loads share one network response`() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val f = Fetcher().apply { beforeResponse = { release.await() } }
        val repo = ChannelNavigationRepository(f, RemoteConfigResolver(f))
        val first = async(start = CoroutineStart.UNDISPATCHED) { repo.loadChannels() }
        val second = async(start = CoroutineStart.UNDISPATCHED) { repo.loadChannels() }
        assertEquals(1, f.requests)
        release.complete(Unit)
        assertEquals(first.await(), second.await())
        assertEquals(1, f.requests)
    }

    @Test fun `cancelled channel load releases lock and does not cache response`() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val f = Fetcher().apply { beforeResponse = { release.await() } }
        val repo = ChannelNavigationRepository(f, RemoteConfigResolver(f))
        val first = async(start = CoroutineStart.UNDISPATCHED) { repo.loadChannels() }
        first.cancelAndJoin()
        release.complete(Unit)
        assertEquals(2, repo.loadChannels().size)
        assertEquals(2, f.requests)
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

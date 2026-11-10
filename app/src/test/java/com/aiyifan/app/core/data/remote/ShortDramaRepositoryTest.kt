package com.aiyifan.app.core.data.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ShortDramaRepositoryTest {
    private class Fetcher(var response: HttpResponse) : HttpFetcher {
        var request = ""
        var cancelled = false
        override suspend fun get(url: String) = HttpResponse(200, """{"api":"https://catalog.example"}""")
        override suspend fun postJson(url: String, body: String): HttpResponse {
            assertEquals("https://catalog.example/api/Home/GetRelativeVideos", url)
            request = body
            if (cancelled) throw CancellationException()
            return response
        }
    }

    private fun payload(items: String, count: Int = 2) = """{"ret":200,"data":{"list":[
        {"type":-1,"list":[{"mediaKey":"ad"}]},
        {"type":8,"name":"任意服务端名称","pageCount":$count,"list":[$items]}
    ]}}"""

    private fun repository(fetcher: Fetcher) = RemoteCatalogRepository(
        configResolver = RemoteConfigResolver(fetcher), fetcher = fetcher,
    )

    @Test fun `requests short category and parses only valid distinct drama cards`() = runBlocking {
        val fetcher = Fetcher(HttpResponse(200, payload("""
            {"mediaKey":"a","title":"短剧","videoType":0,"episodeId":42},
            {"mediaKey":"a","title":"重复"},{"title":"缺少标识"},
            {"mediaKey":"b","title":"另一部"}
        """)))
        val result = repository(fetcher).getShortDramaPage(1)
        val request = JSONObject(fetcher.request)
        assertEquals("8", request.get("titleid"))
        assertEquals("1", request.get("page"))
        assertEquals("30", request.get("size"))
        assertEquals(listOf("a", "b"), result.videos.map { it.mediaKey })
        assertTrue(result.hasMore)
    }

    @Test fun `last page and empty page stop pagination`() = runBlocking {
        val fetcher = Fetcher(HttpResponse(200, payload("""{"mediaKey":"a","title":"A"}""")))
        assertFalse(repository(fetcher).getShortDramaPage(2).hasMore)
        fetcher.response = HttpResponse(200, payload(""))
        assertFalse(repository(fetcher).getShortDramaPage(1).hasMore)
    }

    @Test fun `invalid responses fail without demo fallback even on first page`() = runBlocking {
        for (response in listOf(
            HttpResponse(500, "unavailable"),
            HttpResponse(200, """{"ret":401,"data":{"list":[]}}"""),
            HttpResponse(200, """{"ret":200}"""),
            HttpResponse(200, """{"ret":200,"data":{"list":[{"type":8}]}}"""),
        )) {
            assertTrue(runCatching { repository(Fetcher(response)).getShortDramaPage(1) }.isFailure)
        }
    }

    @Test fun `cancellation propagates`() = runBlocking {
        val fetcher = Fetcher(HttpResponse(200, payload(""))).apply { cancelled = true }
        assertTrue(runCatching { repository(fetcher).getShortDramaPage(1) }.exceptionOrNull() is CancellationException)
    }

    @Test fun `detail episode identity and type drive playback with available quality`() = runBlocking {
        var playbackUrl = ""
        var playable = true
        val fetcher = object : HttpFetcher {
            override suspend fun get(url: String): HttpResponse = when {
                url.contains("VideoDetails") -> HttpResponse(200, """{"ret":200,"data":{"detailInfo":{
                    "mediaKey":"drama","title":"短剧","videoType":1,"episodes":[
                    {"episodeKey":"episode","episodeId":42,"episodeTitle":"01","resolution":"1080"}]}}}""")
                url.contains("getPlayData") -> {
                    playbackUrl = url
                    HttpResponse(200, """{"ret":200,"data":{"list":[
                        {"resolution":"1080","mediaUrl":"","isVip":true},
                        {"resolution":"576","mediaUrl":"${if (playable) "https://example.com/test.m3u8" else ""}"}
                    ]}}""")
                }
                else -> HttpResponse(200, """{"api":"https://catalog.example"}""")
            }
        }
        val repo = RemoteCatalogRepository(configResolver = RemoteConfigResolver(fetcher), fetcher = fetcher)
        val detail = repo.getVideoDetail("drama")
        val episode = detail.episodes.single()
        val result = repo.resolvePlayback(detail, episode)
        assertTrue(playbackUrl.contains("videoId=42"))
        assertTrue(playbackUrl.contains("videoType=1"))
        assertEquals("576", result.resolution)
        assertEquals("episode", result.episodeKey)
        assertNotNull(result.mediaUrl)
        playable = false
        assertNull(repo.resolvePlayback(detail, episode).mediaUrl)
    }
}

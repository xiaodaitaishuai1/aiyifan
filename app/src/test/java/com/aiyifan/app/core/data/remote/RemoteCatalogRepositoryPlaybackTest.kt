package com.aiyifan.app.core.data.remote

import com.aiyifan.app.core.data.FakeCatalogRepository
import com.aiyifan.app.core.model.Episode
import com.aiyifan.app.core.model.VideoDetail
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteCatalogRepositoryPlaybackTest {

    @Test
    fun `forced resolution requests and returns matching playback source`() = runBlocking {
        val fetcher = PlaybackFetcher(
            """{"data":{"list":[
                {"resolution":"720P","mediaUrl":"https://example.com/720.m3u8","opSecond":90,"epSecond":2640},
                {"resolution":"1080P","mediaUrl":"https://example.com/1080.m3u8","isDefault":true}
            ]}}""",
        )

        val result = repository(fetcher).resolvePlayback(
            detail(),
            episode(resolution = "720P"),
            forceRefresh = true,
        )

        assertEquals("720P", fetcher.requestedResolution)
        assertEquals("720P", result.resolution)
        assertEquals("https://example.com/720.m3u8", result.mediaUrl)
        assertEquals(90L, result.opSecond)
        assertEquals(2_640L, result.epSecond)
    }

    @Test
    fun `forced resolution does not fall back to stale direct url on request failure`() = runBlocking {
        val result = repository(PlaybackFetcher("{}", responseCode = 500)).resolvePlayback(
            detail(),
            episode(mediaUrl = "https://example.com/old.m3u8"),
            forceRefresh = true,
        )

        assertNull(result.mediaUrl)
    }

    @Test
    fun `playback response ignores intro and outro values that overflow milliseconds`() = runBlocking {
        val result = repository(
            PlaybackFetcher(
                """{"data":{"list":[{"resolution":"720P","mediaUrl":"https://example.com/720.m3u8","opSecond":9223372036854775807,"epSecond":9223372036854775807}]}}""",
            ),
        ).resolvePlayback(detail(), episode(), forceRefresh = true)

        assertNull(result.opSecond)
        assertNull(result.epSecond)
    }

    private fun repository(fetcher: HttpFetcher) = RemoteCatalogRepository(
        fallback = FakeCatalogRepository(),
        configResolver = RemoteConfigResolver(fetcher),
        fetcher = fetcher,
    )

    private fun detail() = VideoDetail(
        mediaKey = "media-1",
        title = "Sample",
        coverUrl = "",
        videoType = 1,
    )

    private fun episode(
        resolution: String = "720P",
        mediaUrl: String? = null,
    ) = Episode(
        episodeKey = "episode-1",
        episodeTitle = "1",
        uniqueId = 3,
        mediaUrl = mediaUrl,
        resolution = resolution,
    )

    private class PlaybackFetcher(
        private val playbackBody: String,
        private val responseCode: Int = 200,
    ) : HttpFetcher {
        var requestedResolution: String? = null
            private set

        override suspend fun get(url: String): HttpResponse = when {
            url == CONFIG_URL -> HttpResponse(200, """{"api":"$BASE_URL"}""")
            url.startsWith("$BASE_URL/api/Video/getPlayData") -> {
                requestedResolution = url.substringAfter("resolution=").substringBefore('&')
                HttpResponse(responseCode, playbackBody)
            }
            else -> HttpResponse(404, "missing")
        }
    }

    private companion object {
        const val CONFIG_URL = "https://dataexbbff.github.io/rawApp.json"
        const val BASE_URL = "https://catalog.example"
    }
}

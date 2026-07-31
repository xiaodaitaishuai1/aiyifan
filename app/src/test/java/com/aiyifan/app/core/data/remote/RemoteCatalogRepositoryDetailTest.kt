package com.aiyifan.app.core.data.remote

import com.aiyifan.app.core.data.FakeCatalogRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteCatalogRepositoryDetailTest {

    @Test
    fun `detail response retains episode intro and outro seconds`() = runBlocking {
        val repository = RemoteCatalogRepository(
            fallback = FakeCatalogRepository(),
            configResolver = RemoteConfigResolver(DetailFetcher()),
            fetcher = DetailFetcher(),
        )

        val episode = repository.getVideoDetail("media-1").episodes.single()

        assertEquals(90L, episode.opSecond)
        assertEquals(2_640L, episode.epSecond)
    }

    private class DetailFetcher : HttpFetcher {
        override suspend fun get(url: String): HttpResponse = when {
            url == CONFIG_URL -> HttpResponse(200, """{"api":"$BASE_URL"}""")
            url.startsWith("$BASE_URL/api/Video/VideoDetails") -> HttpResponse(
                200,
                """{"data":{"detailInfo":{"mediaKey":"media-1","title":"Sample","coverImgUrl":"","videoType":2,"episodes":[{"episodeKey":"episode-1","episodeTitle":"1","episodeId":1,"opSecond":90,"epSecond":2640}]}}}""",
            )
            else -> HttpResponse(404, "missing")
        }
    }

    private companion object {
        const val CONFIG_URL = "https://dataexbbff.github.io/rawApp.json"
        const val BASE_URL = "https://catalog.example"
    }
}

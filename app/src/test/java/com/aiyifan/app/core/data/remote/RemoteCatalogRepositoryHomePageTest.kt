package com.aiyifan.app.core.data.remote

import com.aiyifan.app.core.data.FakeCatalogRepository
import com.aiyifan.app.core.model.Category
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class RemoteCatalogRepositoryHomePageTest {

    @Test
    fun `loads server category identifiers from the navigation endpoint`() = runBlocking {
        val fetcher = MoviePageFetcher()
        val repository = RemoteCatalogRepository(
            fallback = FakeCatalogRepository(),
            configResolver = RemoteConfigResolver(fetcher),
            fetcher = fetcher,
        )

        val categories = repository.getCategories()

        assertEquals("3", categories.single { it.name == "\u7535\u5f71" }.id)
        assertFalse(categories.any { it.id == "0" })
    }

    @Test
    fun `loads only the selected category from its requested server page`() = runBlocking {
        val fetcher = MoviePageFetcher()
        val repository = RemoteCatalogRepository(
            fallback = FakeCatalogRepository(),
            configResolver = RemoteConfigResolver(fetcher),
            fetcher = fetcher,
        )

        val page = repository.getHomeVideoPage(
            category = Category(id = "3", name = "\u7535\u5f71", type = 1, styleType = 0),
            page = 2,
        )

        assertEquals("https://catalog.example/api/Home/GetRelativeVideos", fetcher.requestedUrl)
        val request = JSONObject(fetcher.requestedJson.orEmpty())
        assertEquals("2", request.getString("page"))
        assertEquals("30", request.getString("size"))
        assertEquals("3", request.getString("titleid"))
        assertEquals(listOf("movie-2", "movie-3"), page.videos.map { it.mediaKey })
        assertTrue(page.hasMore)
    }

    @Test
    fun `later page failure is not converted to an empty fallback page`() = runBlocking {
        val fetcher = object : HttpFetcher {
            override suspend fun get(url: String): HttpResponse =
                HttpResponse(200, """{"api":"https://catalog.example"}""")

            override suspend fun postJson(url: String, body: String): HttpResponse =
                HttpResponse(500, "unavailable")
        }
        val repository = RemoteCatalogRepository(
            fallback = FakeCatalogRepository(),
            configResolver = RemoteConfigResolver(fetcher),
            fetcher = fetcher,
        )

        val result = runCatching {
            repository.getHomeVideoPage(
                category = Category(id = "3", name = "\u7535\u5f71", type = 1, styleType = 0),
                page = 2,
            )
        }

        assertTrue(result.isFailure)
    }

    @Test
    fun `category loading propagates coroutine cancellation`() = runBlocking {
        val fetcher = object : HttpFetcher {
            override suspend fun get(url: String): HttpResponse {
                throw CancellationException("test cancellation")
            }
        }
        val repository = RemoteCatalogRepository(
            fallback = FakeCatalogRepository(),
            configResolver = RemoteConfigResolver(fetcher),
            fetcher = fetcher,
        )

        val result = runCatching { repository.getCategories() }

        assertTrue(result.exceptionOrNull() is CancellationException)
    }

    private class MoviePageFetcher : HttpFetcher {
        var requestedUrl: String? = null
        var requestedJson: String? = null

        override suspend fun get(url: String): HttpResponse =
            HttpResponse(200, """{"api":"https://catalog.example"}""")

        override suspend fun postJson(url: String, body: String): HttpResponse {
            requestedUrl = url
            requestedJson = body
            if (url.endsWith("/api/List/NavigationBar")) {
                return HttpResponse(
                    200,
                    """
                        {
                          "data": {
                            "list": [
                              {"categoryId":0,"name":"\u63a8\u8350","type":0,"styleType":0},
                              {"categoryId":3,"name":"\u7535\u5f71","type":1,"styleType":0}
                            ]
                          }
                        }
                    """.trimIndent(),
                )
            }
            return HttpResponse(
                200,
                """
                    {
                      "data": {
                        "list": [
                          {
                            "name": "\u7535\u89c6\u5267",
                            "list": [{"mediaKey":"drama-1","title":"Drama","videoType":1}]
                          },
                          {
                            "name": "\u7535\u5f71",
                            "list": [
                              {"mediaKey":"movie-2","title":"Movie 2","videoType":1},
                              {"mediaKey":"movie-2","title":"Duplicate","videoType":1},
                              {"mediaKey":"movie-3","title":"Movie 3","videoType":1}
                            ]
                          }
                        ]
                      }
                    }
                """.trimIndent(),
            )
        }
    }
}

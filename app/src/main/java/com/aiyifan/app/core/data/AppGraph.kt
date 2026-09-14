package com.aiyifan.app.core.data

import android.content.Context
import com.aiyifan.app.core.data.remote.RemoteCatalogRepository
import com.aiyifan.app.core.data.remote.RemoteConfigResolver
import com.aiyifan.app.core.data.remote.UrlConnectionHttpFetcher
import com.aiyifan.app.feature.video.VideoPlaybackController
import com.aiyifan.app.feature.video.VideoPlaybackControllerProvider

object AppGraph {
    private lateinit var applicationContext: Context

    val catalogRepository: CatalogRepository by lazy {
        RemoteCatalogRepository(
            configResolver = RemoteConfigResolver(UrlConnectionHttpFetcher()),
            fetcher = UrlConnectionHttpFetcher(),
        )
    }

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }

    private val videoPlaybackControllerProvider by lazy {
        VideoPlaybackControllerProvider {
            check(::applicationContext.isInitialized) {
                "AppGraph must be initialized from AiyifanApp before requesting the video controller."
            }
            VideoPlaybackController.create(
                applicationContext = applicationContext,
                repository = catalogRepository,
            )
        }
    }

    val videoPlaybackController: VideoPlaybackController
        get() = videoPlaybackControllerProvider.get()
}

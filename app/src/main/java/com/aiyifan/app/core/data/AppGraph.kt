package com.aiyifan.app.core.data

import android.content.Context
import com.aiyifan.app.core.data.remote.RemoteCatalogRepository
import com.aiyifan.app.core.data.remote.RemoteConfigResolver
import com.aiyifan.app.core.data.baipiaozhe.PlaybackHttpHeadersHolder
import com.aiyifan.app.core.data.remote.UrlConnectionHttpFetcher
import com.aiyifan.app.feature.proxy.EncryptedProxySettingsStore
import com.aiyifan.app.feature.proxy.HttpSubscriptionContentLoader
import com.aiyifan.app.feature.proxy.ProxyManager
import com.aiyifan.app.feature.proxy.ProxyConnectionListener
import com.aiyifan.app.feature.proxy.ProxyForegroundService
import com.aiyifan.app.feature.proxy.domain.ProxySubscriptionParser
import com.aiyifan.app.feature.proxy.runtime.LibboxSingBoxEngine
import com.aiyifan.app.feature.proxy.runtime.SingBoxConfigurationBuilder
import com.aiyifan.app.feature.proxy.runtime.SingBoxRuntime
import com.aiyifan.app.feature.video.VideoPlaybackController
import com.aiyifan.app.feature.video.VideoPlaybackControllerProvider

object AppGraph {
    private lateinit var applicationContext: Context

    val playbackHeadersHolder by lazy { PlaybackHttpHeadersHolder() }
    val proxyManager: ProxyManager by lazy {
        ProxyManager(
            parser = ProxySubscriptionParser(),
            settingsStore = EncryptedProxySettingsStore(applicationContext),
            subscriptionLoader = HttpSubscriptionContentLoader(UrlConnectionHttpFetcher()),
            runtime = SingBoxRuntime(
                engine = LibboxSingBoxEngine(applicationContext),
                configProvider = SingBoxConfigurationBuilder(),
            ),
            connectionListener = object : ProxyConnectionListener {
                override fun onConnected() {
                    ProxyForegroundService.start(applicationContext)
                }

                override fun onDisconnected() {
                    ProxyForegroundService.stop(applicationContext)
                }
            },
        )
    }

    private val proxyAwareFetcher by lazy {
        UrlConnectionHttpFetcher(endpointProvider = { proxyManager.activeEndpoint })
    }

    val catalogRepository: CatalogRepository by lazy {
        RemoteCatalogRepository(
            configResolver = RemoteConfigResolver(proxyAwareFetcher),
            fetcher = proxyAwareFetcher,
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
                proxyEndpointProvider = { proxyManager.activeEndpoint },
                playbackHeadersProvider = { playbackHeadersHolder.current() },
            )
        }
    }

    val videoPlaybackController: VideoPlaybackController
        get() = videoPlaybackControllerProvider.get()
}

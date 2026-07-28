package com.aiyifan.app.core.ui

import android.content.Context
import com.aiyifan.app.core.data.AppGraph
import com.aiyifan.app.core.data.remote.LocalProxyEndpoint
import com.aiyifan.app.core.data.remote.ProxyAwareImageClientFactory
import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.module.AppGlideModule
import java.io.InputStream

@GlideModule
class ProxyGlideModule : AppGlideModule() {
    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        registry.replace(
            GlideUrl::class.java,
            InputStream::class.java,
            ProxyAwareGlideUrlLoaderFactory { AppGraph.proxyManager.activeEndpoint },
        )
    }

    override fun isManifestParsingEnabled(): Boolean = false
}

private class ProxyAwareGlideUrlLoader(
    private val clientFactory: ProxyAwareImageClientFactory,
) : ModelLoader<GlideUrl, InputStream> {
    override fun buildLoadData(
        model: GlideUrl,
        width: Int,
        height: Int,
        options: com.bumptech.glide.load.Options,
    ): ModelLoader.LoadData<InputStream>? =
        OkHttpUrlLoader(clientFactory.createDirect()).buildLoadData(model, width, height, options)

    override fun handles(model: GlideUrl): Boolean = true
}

private class ProxyAwareGlideUrlLoaderFactory(
    endpointProvider: () -> LocalProxyEndpoint?,
) : ModelLoaderFactory<GlideUrl, InputStream> {
    private val clientFactory = ProxyAwareImageClientFactory(endpointProvider = endpointProvider)

    override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<GlideUrl, InputStream> =
        ProxyAwareGlideUrlLoader(clientFactory)

    override fun teardown() = Unit
}

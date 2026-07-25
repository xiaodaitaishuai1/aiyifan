package com.aiyifan.app.core.data.remote

import okhttp3.OkHttpClient

class ProxyAwareImageClientFactory(
    private val endpointProvider: () -> LocalProxyEndpoint?,
) {
    private var endpoint: LocalProxyEndpoint? = null
    private var client: OkHttpClient? = null

    @Synchronized
    fun create(): OkHttpClient {
        val currentEndpoint = endpointProvider()
        client?.takeIf { endpoint == currentEndpoint }?.let { return it }

        client?.connectionPool?.evictAll()
        endpoint = currentEndpoint
        return OkHttpClient.Builder().apply {
            ProxyConnectionPolicy.select(currentEndpoint)?.let(::proxy)
        }.build().also { client = it }
    }
}

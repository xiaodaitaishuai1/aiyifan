package com.aiyifan.app.core.data.remote

import okhttp3.OkHttpClient
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI

class ProxyAwareImageClientFactory(
    private val endpointValidator: (LocalProxyEndpoint) -> Boolean = ::isEndpointReachable,
    private val endpointProvider: () -> LocalProxyEndpoint?,
) {
    private var endpoint: LocalProxyEndpoint? = null
    private var client: OkHttpClient? = null
    private val directClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)
            .build()
    }

    fun createDirect(): OkHttpClient = directClient

    @Synchronized
    fun create(): OkHttpClient {
        val currentEndpoint = endpointProvider()?.takeIf(endpointValidator)
        client?.takeIf { endpoint == currentEndpoint }?.let { return it }

        client?.connectionPool?.evictAll()
        endpoint = currentEndpoint
        return OkHttpClient.Builder().apply {
            currentEndpoint?.let { endpoint ->
                proxySelector(ProxyThenDirectSelector(endpoint))
            }
        }.build().also { client = it }
    }

    private class ProxyThenDirectSelector(endpoint: LocalProxyEndpoint) : ProxySelector() {
        private val proxy = checkNotNull(ProxyConnectionPolicy.select(endpoint))

        override fun select(uri: URI?): List<Proxy> = listOf(proxy, Proxy.NO_PROXY)

        override fun connectFailed(uri: URI?, socketAddress: SocketAddress?, exception: java.io.IOException?) = Unit
    }

    private companion object {
        const val ENDPOINT_CONNECT_TIMEOUT_MS = 200

        fun isEndpointReachable(endpoint: LocalProxyEndpoint): Boolean =
            runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(endpoint.host, endpoint.port), ENDPOINT_CONNECT_TIMEOUT_MS)
                }
            }.isSuccess
    }
}

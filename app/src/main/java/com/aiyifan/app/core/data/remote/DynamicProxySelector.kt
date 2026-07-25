package com.aiyifan.app.core.data.remote

import java.io.IOException
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

class DynamicProxySelector(
    private val endpointProvider: () -> LocalProxyEndpoint?,
) : ProxySelector() {
    override fun select(uri: URI): List<Proxy> =
        listOf(ProxyConnectionPolicy.select(endpointProvider()) ?: Proxy.NO_PROXY)

    override fun connectFailed(uri: URI, socketAddress: SocketAddress, exception: IOException) = Unit
}

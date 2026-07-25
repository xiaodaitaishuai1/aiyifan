package com.aiyifan.app.core.data.remote

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Test

class DynamicProxySelectorTest {

    @Test
    fun `select uses active local endpoint as SOCKS proxy`() {
        val selector = DynamicProxySelector { LocalProxyEndpoint("127.0.0.1", 2080) }

        val proxy = selector.select(URI("https://static.tripdata.app/poster.jpg")).single()

        assertEquals(Proxy.Type.SOCKS, proxy.type())
        val address = proxy.address() as InetSocketAddress
        assertEquals("127.0.0.1", address.hostString)
        assertEquals(2080, address.port)
    }

    @Test
    fun `select uses direct connection when no endpoint is active`() {
        val selector = DynamicProxySelector { null }

        val proxy = selector.select(URI("https://static.tripdata.app/poster.jpg")).single()

        assertEquals(Proxy.NO_PROXY, proxy)
    }

    @Test
    fun `connect failed does not change selection behavior`() {
        val selector = DynamicProxySelector { null }

        selector.connectFailed(
            URI("https://static.tripdata.app/poster.jpg"),
            InetSocketAddress("127.0.0.1", 2080),
            IOException("connection refused"),
        )

        assertEquals(Proxy.NO_PROXY, selector.select(URI("https://static.tripdata.app/poster.jpg")).single())
    }
}

package com.aiyifan.app.core.data.remote

import java.net.InetSocketAddress
import java.net.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ProxyAwareImageClientFactoryTest {

    private var endpoint: LocalProxyEndpoint? = null
    private val factory = ProxyAwareImageClientFactory { endpoint }

    @Test
    fun `client uses active local endpoint as SOCKS proxy`() {
        endpoint = LocalProxyEndpoint("127.0.0.1", 2080)

        val proxy = factory.create().proxy

        assertEquals(Proxy.Type.SOCKS, proxy?.type())
        val address = proxy?.address() as InetSocketAddress
        assertEquals("127.0.0.1", address.hostString)
        assertEquals(2080, address.port)
    }

    @Test
    fun `client uses default direct routing when no endpoint is active`() {
        endpoint = null

        assertNull(factory.create().proxy)
    }

    @Test
    fun `client is replaced when active endpoint changes`() {
        val directClient = factory.create()
        assertSame(directClient, factory.create())
        endpoint = LocalProxyEndpoint("127.0.0.1", 2080)
        val proxyClient = factory.create()
        endpoint = null
        val replacementDirectClient = factory.create()

        assertNotSame(directClient, proxyClient)
        assertNotSame(proxyClient, replacementDirectClient)
    }
}

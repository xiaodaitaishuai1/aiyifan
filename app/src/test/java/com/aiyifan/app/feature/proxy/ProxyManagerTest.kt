package com.aiyifan.app.feature.proxy

import com.aiyifan.app.core.data.remote.LocalProxyEndpoint
import com.aiyifan.app.feature.proxy.domain.ProxyNode
import com.aiyifan.app.feature.proxy.domain.ProxyProtocol
import com.aiyifan.app.feature.proxy.domain.ProxySubscriptionParser
import com.aiyifan.app.feature.proxy.domain.SubscriptionImportResult
import com.aiyifan.app.feature.proxy.runtime.SingBoxConfigProvider
import com.aiyifan.app.feature.proxy.runtime.SingBoxEngine
import com.aiyifan.app.feature.proxy.runtime.SingBoxRuntime
import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyManagerTest {

    @Test
    fun `refreshes subscription directly, remembers selected node and starts local endpoint`() = runBlocking {
        val store = FakeSettingsStore()
        val loader = FakeSubscriptionLoader(encodedSubscription())
        val engine = RecordingEngine()
        val listener = RecordingConnectionListener()
        val manager = ProxyManager(
            parser = ProxySubscriptionParser(::decodeWithJvmBase64),
            settingsStore = store,
            subscriptionLoader = loader,
            runtime = SingBoxRuntime(engine, HostConfigProvider()),
            connectionListener = listener,
        )

        val result = manager.refresh("https://subscription.example.com/token")
        manager.select(manager.nodes.single().id)
        val endpoint = manager.connect()

        assertTrue(result is SubscriptionImportResult.Imported)
        assertEquals("https://subscription.example.com/token", loader.loadedUrl)
        assertEquals("https://subscription.example.com/token", store.subscriptionUrl)
        assertEquals(manager.selectedNode?.id, store.selectedNodeId)
        assertEquals(LocalProxyEndpoint("127.0.0.1", 2080), endpoint)
        assertEquals("edge.example.com", engine.startedForHost)
        assertEquals(1, listener.connectedCalls)
    }

    @Test
    fun `refresh disconnects an active node before replacing imported nodes`() = runBlocking {
        val engine = RecordingEngine()
        val manager = ProxyManager(
            parser = ProxySubscriptionParser(::decodeWithJvmBase64),
            settingsStore = FakeSettingsStore(),
            subscriptionLoader = FakeSubscriptionLoader(encodedSubscription()),
            runtime = SingBoxRuntime(engine, HostConfigProvider()),
        )
        manager.refresh("https://subscription.example.com/one")
        manager.connect()

        manager.refresh("https://subscription.example.com/two")

        assertEquals(1, engine.stopCalls)
        assertEquals(null, manager.activeEndpoint)
    }

    @Test
    fun `records a safe failure stage when the local proxy cannot start`() = runBlocking {
        val manager = ProxyManager(
            parser = ProxySubscriptionParser(::decodeWithJvmBase64),
            settingsStore = FakeSettingsStore(),
            subscriptionLoader = FakeSubscriptionLoader(encodedSubscription()),
            runtime = SingBoxRuntime(FailingEngine(), HostConfigProvider()),
        )
        manager.refresh("https://subscription.example.com/one")

        val endpoint = manager.connect()

        assertEquals(null, endpoint)
        assertEquals(ProxyConnectionFailure.UNKNOWN, manager.lastConnectionFailure)
    }

    private fun encodedSubscription(): String = Base64.getEncoder().encodeToString(
        "vless://123e4567-e89b-12d3-a456-426614174000@edge.example.com:443?encryption=none#Edge".toByteArray(),
    )

    private fun decodeWithJvmBase64(encoded: String, urlSafe: Boolean): ByteArray? = runCatching {
        if (urlSafe) Base64.getUrlDecoder().decode(encoded) else Base64.getDecoder().decode(encoded)
    }.getOrNull()

    private class FakeSettingsStore : ProxySettingsStore {
        var subscriptionUrl: String? = null
        var selectedNodeId: String? = null

        override fun readSubscriptionUrl(): String? = subscriptionUrl

        override fun saveSubscriptionUrl(value: String) {
            subscriptionUrl = value
        }

        override fun readSelectedNodeId(): String? = selectedNodeId

        override fun saveSelectedNodeId(value: String?) {
            selectedNodeId = value
        }
    }

    private class FakeSubscriptionLoader(private val content: String) : SubscriptionContentLoader {
        var loadedUrl: String? = null

        override suspend fun loadDirect(url: String): String {
            loadedUrl = url
            return content
        }
    }

    private class RecordingEngine : SingBoxEngine {
        var startedForHost: String? = null
        var stopCalls = 0

        override fun start(config: String) {
            startedForHost = config
        }

        override fun stop() {
            stopCalls += 1
        }
    }

    private class FailingEngine : SingBoxEngine {
        override fun start(config: String) {
            error("start failed")
        }

        override fun stop() = Unit
    }

    private class RecordingConnectionListener : ProxyConnectionListener {
        var connectedCalls = 0

        override fun onConnected() {
            connectedCalls += 1
        }

        override fun onDisconnected() = Unit
    }

    private class HostConfigProvider : SingBoxConfigProvider {
        override fun build(node: ProxyNode, localPort: Int): String = node.host
    }
}

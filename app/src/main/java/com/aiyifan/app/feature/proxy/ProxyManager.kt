package com.aiyifan.app.feature.proxy

import com.aiyifan.app.core.data.remote.LocalProxyEndpoint
import com.aiyifan.app.feature.proxy.domain.ProxyConnectionState
import com.aiyifan.app.feature.proxy.domain.ProxyConnectionStateMachine
import com.aiyifan.app.feature.proxy.domain.ProxyNode
import com.aiyifan.app.feature.proxy.domain.ProxyProtocol
import com.aiyifan.app.feature.proxy.domain.ProxySubscriptionParser
import com.aiyifan.app.feature.proxy.domain.SubscriptionImportResult
import com.aiyifan.app.feature.proxy.runtime.SingBoxRuntime
import com.aiyifan.app.feature.proxy.runtime.SingBoxStartupException
import com.aiyifan.app.feature.proxy.runtime.SingBoxStartupStage

interface ProxySettingsStore {
    fun readSubscriptionUrl(): String?

    fun saveSubscriptionUrl(value: String)

    fun readSelectedNodeId(): String?

    fun saveSelectedNodeId(value: String?)
}

fun interface SubscriptionContentLoader {
    suspend fun loadDirect(url: String): String
}

enum class ProxyConnectionFailure {
    CONFIGURATION,
    SERVICE_CREATION,
    SERVICE_START,
    UNKNOWN,
}

interface ProxyConnectionListener {
    fun onConnected()

    fun onDisconnected()
}

private object NoOpProxyConnectionListener : ProxyConnectionListener {
    override fun onConnected() = Unit

    override fun onDisconnected() = Unit
}

class ProxyManager(
    private val parser: ProxySubscriptionParser,
    private val settingsStore: ProxySettingsStore,
    private val subscriptionLoader: SubscriptionContentLoader,
    private val runtime: SingBoxRuntime,
    private val stateMachine: ProxyConnectionStateMachine = ProxyConnectionStateMachine(),
    private val connectionListener: ProxyConnectionListener = NoOpProxyConnectionListener,
) {
    var nodes: List<ProxyNode> = emptyList()
        private set
    var selectedNode: ProxyNode? = null
        private set
    var lastConnectionFailure: ProxyConnectionFailure? = null
        private set

    val state: ProxyConnectionState
        get() = stateMachine.state

    val activeEndpoint: LocalProxyEndpoint?
        get() = runtime.activeEndpoint

    fun storedSubscriptionUrl(): String? = settingsStore.readSubscriptionUrl()

    suspend fun refresh(url: String): SubscriptionImportResult {
        val result = parser.parse(subscriptionLoader.loadDirect(url))
        if (result is SubscriptionImportResult.Imported) {
            disconnect()
            nodes = result.nodes.filter { it.protocol == ProxyProtocol.VLESS }
            selectedNode = nodes.firstOrNull { it.id == settingsStore.readSelectedNodeId() }
                ?: nodes.firstOrNull()
            settingsStore.saveSubscriptionUrl(url)
            settingsStore.saveSelectedNodeId(selectedNode?.id)
        }
        return result
    }

    fun select(nodeId: String): Boolean {
        val node = nodes.firstOrNull { it.id == nodeId && it.protocol == ProxyProtocol.VLESS } ?: return false
        if (selectedNode?.id != node.id) disconnect()
        selectedNode = node
        settingsStore.saveSelectedNodeId(node.id)
        return true
    }

    fun connect(): LocalProxyEndpoint? {
        val node = selectedNode ?: return null
        if (state is ProxyConnectionState.Connected && activeEndpoint != null) return activeEndpoint
        lastConnectionFailure = null
        stateMachine.connect(node)
        val attempt = stateMachine.activeAttempt() ?: return activeEndpoint
        return try {
            runtime.connect(node).also {
                stateMachine.completeConnection(attempt)
                connectionListener.onConnected()
            }
        } catch (error: Throwable) {
            stateMachine.failConnection(attempt)
            lastConnectionFailure = error.toConnectionFailure()
            connectionListener.onDisconnected()
            null
        }
    }

    fun disconnect() {
        runtime.disconnect()
        stateMachine.disconnect()
        lastConnectionFailure = null
        connectionListener.onDisconnected()
    }

    private fun Throwable.toConnectionFailure(): ProxyConnectionFailure = when (
        (this as? SingBoxStartupException)?.stage
    ) {
        SingBoxStartupStage.CONFIGURATION -> ProxyConnectionFailure.CONFIGURATION
        SingBoxStartupStage.SERVICE_CREATION -> ProxyConnectionFailure.SERVICE_CREATION
        SingBoxStartupStage.SERVICE_START -> ProxyConnectionFailure.SERVICE_START
        null -> ProxyConnectionFailure.UNKNOWN
    }
}

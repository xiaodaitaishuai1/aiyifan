package com.aiyifan.app.feature.proxy

import com.aiyifan.app.core.data.remote.LocalProxyEndpoint

object ProxySubscriptionRestorePolicy {
    fun shouldRestore(nodeCount: Int, activeEndpoint: LocalProxyEndpoint?): Boolean =
        nodeCount == 0 && activeEndpoint == null
}

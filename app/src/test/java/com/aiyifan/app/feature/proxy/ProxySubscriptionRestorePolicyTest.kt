package com.aiyifan.app.feature.proxy

import com.aiyifan.app.core.data.remote.LocalProxyEndpoint
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxySubscriptionRestorePolicyTest {

    @Test
    fun `restores a saved subscription only when no in-memory proxy state exists`() {
        assertTrue(ProxySubscriptionRestorePolicy.shouldRestore(nodeCount = 0, activeEndpoint = null))
        assertFalse(ProxySubscriptionRestorePolicy.shouldRestore(nodeCount = 1, activeEndpoint = null))
        assertFalse(
            ProxySubscriptionRestorePolicy.shouldRestore(
                nodeCount = 0,
                activeEndpoint = LocalProxyEndpoint("127.0.0.1", 2080),
            ),
        )
    }
}

package com.aiyifan.app.feature.home

import com.aiyifan.app.R
import com.aiyifan.app.feature.proxy.domain.ProxyConnectionState
import com.aiyifan.app.feature.proxy.domain.ProxyNode
import com.aiyifan.app.feature.proxy.domain.ProxyProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeVpnQuickConnectPresentationTest {

    @Test
    fun `hides quick connect when no previous connection exists`() {
        val presentation = HomeVpnQuickConnectPresentation.resolve(
            hasConnectedBefore = false,
            isConnecting = false,
            connectionState = ProxyConnectionState.Disconnected,
        )

        assertFalse(presentation.isVisible)
    }

    @Test
    fun `shows connecting while a quick connection is in progress`() {
        val presentation = HomeVpnQuickConnectPresentation.resolve(
            hasConnectedBefore = true,
            isConnecting = true,
            connectionState = ProxyConnectionState.Disconnected,
        )

        assertTrue(presentation.isVisible)
        assertFalse(presentation.isEnabled)
        assertEquals(R.string.home_vpn_connecting, presentation.textRes)
    }

    @Test
    fun `shows connected state without allowing another connection`() {
        val presentation = HomeVpnQuickConnectPresentation.resolve(
            hasConnectedBefore = true,
            isConnecting = false,
            connectionState = ProxyConnectionState.Connected(testNode()),
        )

        assertTrue(presentation.isVisible)
        assertFalse(presentation.isEnabled)
        assertEquals(R.string.home_vpn_connected, presentation.textRes)
    }

    @Test
    fun `disables quick connect while another connection is in progress`() {
        val presentation = HomeVpnQuickConnectPresentation.resolve(
            hasConnectedBefore = true,
            isConnecting = false,
            connectionState = ProxyConnectionState.Connecting(testNode()),
        )

        assertFalse(presentation.isEnabled)
        assertEquals(R.string.home_vpn_connecting, presentation.textRes)
    }

    private fun testNode() = ProxyNode.forTesting(
        displayName = "Node",
        protocol = ProxyProtocol.VLESS,
        host = "edge.example.com",
        port = 443,
    )
}

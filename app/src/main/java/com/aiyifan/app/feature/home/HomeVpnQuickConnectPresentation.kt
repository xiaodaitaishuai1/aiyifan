package com.aiyifan.app.feature.home

import androidx.annotation.StringRes
import com.aiyifan.app.R
import com.aiyifan.app.feature.proxy.domain.ProxyConnectionState

data class HomeVpnQuickConnectPresentation(
    val isVisible: Boolean,
    val isEnabled: Boolean,
    @param:StringRes val textRes: Int,
) {
    companion object {
        fun resolve(
            hasConnectedBefore: Boolean,
            isConnecting: Boolean,
            connectionState: ProxyConnectionState,
        ): HomeVpnQuickConnectPresentation {
            val isConnectionInProgress = isConnecting || connectionState is ProxyConnectionState.Connecting
            return HomeVpnQuickConnectPresentation(
                isVisible = hasConnectedBefore && connectionState !is ProxyConnectionState.Connected,
                isEnabled = hasConnectedBefore && !isConnectionInProgress && connectionState !is ProxyConnectionState.Connected,
                textRes = when {
                    isConnectionInProgress -> R.string.home_vpn_connecting
                    connectionState is ProxyConnectionState.Connected -> R.string.home_vpn_connected
                    else -> R.string.home_vpn_connect
                },
            )
        }
    }
}

package com.aiyifan.app.feature.proxy.runtime

import android.content.Context
import android.net.ConnectivityManager
import android.net.DnsResolver
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.system.ErrnoException
import io.nekohasekai.libbox.ExchangeContext
import io.nekohasekai.libbox.Func
import io.nekohasekai.libbox.LocalDNSTransport
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Selects the underlying Internet network instead of Android's default VPN network.
 * This mirrors the strategy used by the sing-box Android client.
 */
internal class ProxyDefaultNetworkMonitor(context: Context) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val request = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
        .build()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var currentNetwork: Network? = null
    private var callback: ConnectivityManager.NetworkCallback? = null
    private var listener: ((Network?) -> Unit)? = null

    fun start(listener: (Network?) -> Unit) {
        if (callback != null) return
        this.listener = listener
        currentNetwork = connectivity.activeNetwork
        listener(currentNetwork)
        callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = publish(network)

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                publish(network)
            }

            override fun onLost(network: Network) {
                if (currentNetwork == network) publish(null)
            }
        }.also(::register)
    }

    fun currentNetwork(): Network? = currentNetwork ?: connectivity.activeNetwork

    fun close() {
        callback?.let { registeredCallback ->
            runCatching { connectivity.unregisterNetworkCallback(registeredCallback) }
        }
        callback = null
        listener = null
        currentNetwork = null
    }

    private fun register(networkCallback: ConnectivityManager.NetworkCallback) {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                connectivity.registerBestMatchingNetworkCallback(request, networkCallback, mainHandler)
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> {
                connectivity.requestNetwork(request, networkCallback)
            }

            else -> connectivity.registerDefaultNetworkCallback(networkCallback)
        }
    }

    private fun publish(network: Network?) {
        currentNetwork = network
        listener?.invoke(network)
    }
}

/** Android system DNS bound to the same physical network selected above. */
internal class ProxyLocalResolver(
    private val networkProvider: () -> Network?,
    private val callbackExecutor: Executor = Dispatchers.IO.asExecutor(),
) : LocalDNSTransport {

    override fun raw(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    override fun exchange(context: ExchangeContext, message: ByteArray) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            context.errorCode(RCODE_SERVFAIL)
            return
        }
        runBlocking {
            val network = networkProvider()
            if (network == null) {
                context.errorCode(RCODE_SERVFAIL)
                return@runBlocking
            }
            suspendCancellableCoroutine { continuation ->
                val cancellation = CancellationSignal()
                context.onCancel(Func { cancellation.cancel() })
                DnsResolver.getInstance().rawQuery(
                    network,
                    message,
                    DnsResolver.FLAG_NO_RETRY,
                    callbackExecutor,
                    cancellation,
                    object : DnsResolver.Callback<ByteArray> {
                        override fun onAnswer(answer: ByteArray, rcode: Int) {
                            if (rcode == NO_ERROR) context.rawSuccess(answer) else context.errorCode(rcode)
                            if (continuation.isActive) continuation.resume(Unit)
                        }

                        override fun onError(error: DnsResolver.DnsException) {
                            val errno = (error.cause as? ErrnoException)?.errno
                            if (errno != null) context.errnoCode(errno) else context.errorCode(RCODE_SERVFAIL)
                            if (continuation.isActive) continuation.resume(Unit)
                        }
                    },
                )
            }
        }
    }

    override fun lookup(context: ExchangeContext, network: String, domain: String) {
        runBlocking {
            val selectedNetwork = networkProvider()
            if (selectedNetwork == null) {
                context.errorCode(RCODE_SERVFAIL)
                return@runBlocking
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                val addresses = try {
                    selectedNetwork.getAllByName(domain)
                } catch (_: UnknownHostException) {
                    context.errorCode(RCODE_NXDOMAIN)
                    return@runBlocking
                }
                context.success(addresses.joinToString("\n") { it.hostAddress.orEmpty() })
                return@runBlocking
            }
            suspendCancellableCoroutine { continuation ->
                val cancellation = CancellationSignal()
                context.onCancel(Func { cancellation.cancel() })
                val queryType = when {
                    network.endsWith("4") -> DnsResolver.TYPE_A
                    network.endsWith("6") -> DnsResolver.TYPE_AAAA
                    else -> null
                }
                val callback = object : DnsResolver.Callback<Collection<InetAddress>> {
                    override fun onAnswer(answer: Collection<InetAddress>, rcode: Int) {
                        if (rcode == NO_ERROR) {
                            context.success(answer.joinToString("\n") { it.hostAddress.orEmpty() })
                        } else {
                            context.errorCode(rcode)
                        }
                        if (continuation.isActive) continuation.resume(Unit)
                    }

                    override fun onError(error: DnsResolver.DnsException) {
                        val errno = (error.cause as? ErrnoException)?.errno
                        if (errno != null) context.errnoCode(errno) else context.errorCode(RCODE_SERVFAIL)
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                }
                val resolver = DnsResolver.getInstance()
                if (queryType == null) {
                    resolver.query(
                        selectedNetwork,
                        domain,
                        DnsResolver.FLAG_NO_RETRY,
                        callbackExecutor,
                        cancellation,
                        callback,
                    )
                } else {
                    resolver.query(
                        selectedNetwork,
                        domain,
                        queryType,
                        DnsResolver.FLAG_NO_RETRY,
                        callbackExecutor,
                        cancellation,
                        callback,
                    )
                }
            }
        }
    }

    private companion object {
        const val NO_ERROR = 0
        const val RCODE_NXDOMAIN = 3
        const val RCODE_SERVFAIL = 2
    }
}

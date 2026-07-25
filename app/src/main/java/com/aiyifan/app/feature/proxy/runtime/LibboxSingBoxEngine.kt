package com.aiyifan.app.feature.proxy.runtime

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.system.OsConstants
import android.util.Base64
import android.util.Log
import io.nekohasekai.libbox.BoxService
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.LocalDNSTransport
import io.nekohasekai.libbox.NetworkInterface
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.Notification
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.SetupOptions
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.libbox.WIFIState
import java.net.Inet6Address
import java.net.InterfaceAddress
import java.net.NetworkInterface as JavaNetworkInterface
import java.security.KeyStore
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

enum class SingBoxStartupStage {
    CONFIGURATION,
    SERVICE_CREATION,
    SERVICE_START,
}

class SingBoxStartupException(
    val stage: SingBoxStartupStage,
    cause: Throwable,
) : RuntimeException("sing-box startup failed at $stage", cause)

class LibboxSingBoxEngine(context: Context) : SingBoxEngine {
    private val applicationContext = context.applicationContext
    private var service: BoxService? = null
    private var platform: LocalProxyPlatform? = null

    init {
        ensureSetup(applicationContext)
    }

    override fun start(config: String) {
        stop()
        try {
            Libbox.checkConfig(config)
        } catch (error: Throwable) {
            throw SingBoxStartupException(SingBoxStartupStage.CONFIGURATION, error)
        }
        val platform = LocalProxyPlatform(applicationContext).also { it.startNetworkMonitoring() }
        val newService = try {
            Libbox.newService(config, platform)
        } catch (error: Throwable) {
            platform.closeNetworkMonitoring()
            throw SingBoxStartupException(SingBoxStartupStage.SERVICE_CREATION, error)
        }
        try {
            newService.start()
        } catch (error: Throwable) {
            newService.close()
            platform.closeNetworkMonitoring()
            throw SingBoxStartupException(SingBoxStartupStage.SERVICE_START, error)
        }
        service = newService
        this.platform = platform
    }

    override fun stop() {
        service?.close()
        service = null
        platform?.closeNetworkMonitoring()
        platform = null
    }

    private class LocalProxyPlatform(context: Context) : PlatformInterface {
        private val connectivity = context.getSystemService(ConnectivityManager::class.java)
        private val networkCallbackExecutor: ExecutorService = Executors.newSingleThreadExecutor()
        private val networkMonitor = ProxyDefaultNetworkMonitor(context)
        private val localResolver = ProxyLocalResolver(networkMonitor::currentNetwork)
        private var defaultNetworkListener: InterfaceUpdateListener? = null

        fun startNetworkMonitoring() {
            networkMonitor.start(::publishDefaultNetwork)
        }

        fun closeNetworkMonitoring() {
            networkMonitor.close()
            defaultNetworkListener = null
            networkCallbackExecutor.shutdownNow()
        }

        override fun autoDetectInterfaceControl(fd: Int) = Unit

        override fun clearDNSCache() = Unit

        override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
            if (defaultNetworkListener !== listener) return
            closeNetworkMonitoring()
        }

        override fun findConnectionOwner(
            ipProtocol: Int,
            sourceAddress: String,
            sourcePort: Int,
            destinationAddress: String,
            destinationPort: Int,
        ): Int = 0

        override fun getInterfaces(): NetworkInterfaceIterator {
            val interfaces = connectivity.allNetworks.mapNotNull(::networkInterface)
            return NetworkInterfaceListIterator(interfaces.iterator())
        }

        override fun includeAllNetworks(): Boolean = false

        override fun localDNSTransport(): LocalDNSTransport = localResolver

        override fun openTun(options: TunOptions): Int =
            throw UnsupportedOperationException("TUN is not used by the local proxy")

        override fun packageNameByUid(uid: Int): String = ""

        override fun readWIFIState(): WIFIState? = null

        override fun sendNotification(notification: Notification) = Unit

        override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
            defaultNetworkListener = listener
            publishDefaultNetwork(networkMonitor.currentNetwork())
        }

        override fun systemCertificates(): StringIterator = CertificateIterator(loadSystemCertificates().iterator())

        override fun uidByPackageName(packageName: String): Int = -1

        override fun underNetworkExtension(): Boolean = false

        override fun usePlatformAutoDetectInterfaceControl(): Boolean = true

        override fun useProcFS(): Boolean = false

        override fun writeLog(message: String) {
            Log.w(LOG_TAG, "sing_box=${SingBoxLogClassifier.classify(message)}")
        }

        private fun publishDefaultNetwork(network: Network?) {
            val listener = defaultNetworkListener ?: return
            val linkProperties = network?.let(connectivity::getLinkProperties)
            val capabilities = network?.let(connectivity::getNetworkCapabilities)
            val interfaceName = linkProperties?.interfaceName
            val index = interfaceName?.let { JavaNetworkInterface.getByName(it)?.index } ?: -1
            val isExpensive = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false
            val isConstrained = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED) == false
            // libbox can call this while it is starting on a Go-owned thread. Re-entering it
            // synchronously deadlocks the startup path, so mirror the official Android bridge.
            networkCallbackExecutor.execute {
                listener.updateDefaultInterface(interfaceName.orEmpty(), index, isExpensive, isConstrained)
            }
        }

        private fun networkInterface(network: Network): NetworkInterface? {
            val linkProperties = connectivity.getLinkProperties(network) ?: return null
            val capabilities = connectivity.getNetworkCapabilities(network) ?: return null
            val javaInterface = JavaNetworkInterface.getByName(linkProperties.interfaceName) ?: return null
            return NetworkInterface().apply {
                name = javaInterface.name
                index = javaInterface.index
                mtu = 1500
                addresses = CertificateIterator(javaInterface.interfaceAddresses.map { it.toPrefix() }.iterator())
                dnsServer = CertificateIterator(linkProperties.dnsServers.mapNotNull { it.hostAddress }.iterator())
                type = when {
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Libbox.InterfaceTypeWIFI
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Libbox.InterfaceTypeCellular
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Libbox.InterfaceTypeEthernet
                    else -> Libbox.InterfaceTypeOther
                }
                flags = interfaceFlags(javaInterface, capabilities)
                metered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            }
        }

        private fun interfaceFlags(
            networkInterface: JavaNetworkInterface,
            capabilities: NetworkCapabilities,
        ): Int {
            var flags = if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                OsConstants.IFF_UP or OsConstants.IFF_RUNNING
            } else {
                0
            }
            if (networkInterface.isLoopback) flags = flags or OsConstants.IFF_LOOPBACK
            if (networkInterface.isPointToPoint) flags = flags or OsConstants.IFF_POINTOPOINT
            if (networkInterface.supportsMulticast()) flags = flags or OsConstants.IFF_MULTICAST
            return flags
        }

        private fun loadSystemCertificates(): List<String> {
            val keyStore = KeyStore.getInstance("AndroidCAStore")
            keyStore.load(null, null)
            val aliases = keyStore.aliases()
            return generateSequence { if (aliases.hasMoreElements()) aliases.nextElement() else null }
                .mapNotNull(keyStore::getCertificate)
                .map { certificate ->
                    "-----BEGIN CERTIFICATE-----\n" +
                        Base64.encodeToString(certificate.encoded, Base64.NO_WRAP) +
                        "\n-----END CERTIFICATE-----"
                }
                .toList()
        }

        private fun InterfaceAddress.toPrefix(): String = if (address is Inet6Address) {
            "${Inet6Address.getByAddress(address.address).hostAddress}/$networkPrefixLength"
        } else {
            "${address.hostAddress}/$networkPrefixLength"
        }
    }

    private class NetworkInterfaceListIterator(
        private val iterator: Iterator<NetworkInterface>,
    ) : NetworkInterfaceIterator {
        override fun hasNext(): Boolean = iterator.hasNext()

        override fun next(): NetworkInterface? = iterator.next()
    }

    private class CertificateIterator(
        private val iterator: Iterator<String>,
    ) : StringIterator {
        override fun hasNext(): Boolean = iterator.hasNext()

        override fun len(): Int = 0

        override fun next(): String? = iterator.next()
    }

    private companion object {
        const val LOG_TAG = "InAppProxy"
        private var isSetup = false

        @Synchronized
        private fun ensureSetup(context: Context) {
            if (isSetup) return
            val baseDirectory = context.filesDir.resolve("libbox").apply { mkdirs() }
            val tempDirectory = context.cacheDir.resolve("libbox").apply { mkdirs() }
            Libbox.setup(
                SetupOptions().apply {
                    basePath = baseDirectory.absolutePath
                    workingPath = baseDirectory.absolutePath
                    tempPath = tempDirectory.absolutePath
                    fixAndroidStack = true
                },
            )
            isSetup = true
        }
    }
}

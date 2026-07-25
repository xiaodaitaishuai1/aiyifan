package com.aiyifan.app.feature.proxy.runtime

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aiyifan.app.feature.proxy.domain.ProxyNode
import com.aiyifan.app.feature.proxy.domain.ProxyProtocol
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.SetupOptions
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibboxConfigurationInstrumentedTest {

    @Test
    fun realityVlessConfigurationIsAcceptedByBundledLibbox() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val directory = File(context.cacheDir, "libbox-test").apply { mkdirs() }
        Libbox.setup(
            SetupOptions().apply {
                basePath = directory.absolutePath
                workingPath = directory.absolutePath
                tempPath = directory.absolutePath
                fixAndroidStack = true
            },
        )
        val node = ProxyNode(
            protocol = ProxyProtocol.VLESS,
            displayName = "Safe node",
            host = "edge.example.com",
            port = 443,
            serializedNode = "vless://123e4567-e89b-12d3-a456-426614174000@edge.example.com:443" +
                "?encryption=none&security=reality&sni=cdn.example.com&pbk=2lqrdi0ux_sEmBfSFizTuE9fJ14aUYU-xmnUHSFalAw&sid=abcd" +
                "&fp=chrome&flow=xtls-rprx-vision&type=tcp#Safe",
        )

        Libbox.checkConfig(SingBoxConfigurationBuilder().build(node, localPort = 2080))
    }
}

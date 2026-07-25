package com.aiyifan.app.feature.proxy.runtime

import com.aiyifan.app.feature.proxy.domain.ProxySubscriptionParser
import com.aiyifan.app.feature.proxy.domain.SubscriptionImportResult
import java.util.Base64
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SingBoxConfigurationBuilderTest {

    @Test
    fun `builds loopback mixed inbound and VLESS outbound`() {
        val node = importedNode(
            "vless://123e4567-e89b-12d3-a456-426614174000@edge.example.com:443" +
                "?encryption=none&security=reality&sni=cdn.example.com&pbk=public-key&sid=abcd&fp=chrome&insecure=1&type=tcp#Edge",
        )

        val config = JSONObject(SingBoxConfigurationBuilder().build(node, localPort = 2080))

        val inbound = config.getJSONArray("inbounds").getJSONObject(0)
        assertEquals("mixed", inbound.getString("type"))
        assertEquals("127.0.0.1", inbound.getString("listen"))
        assertEquals(2080, inbound.getInt("listen_port"))

        val outbound = config.getJSONArray("outbounds").getJSONObject(0)
        assertEquals("vless", outbound.getString("type"))
        assertFalse(outbound.has("encryption"))
        assertEquals("edge.example.com", outbound.getString("server"))
        assertEquals(443, outbound.getInt("server_port"))
        assertEquals("123e4567-e89b-12d3-a456-426614174000", outbound.getString("uuid"))
        val tls = outbound.getJSONObject("tls")
        assertEquals("cdn.example.com", tls.getString("server_name"))
        assertEquals(true, tls.getBoolean("insecure"))
        assertEquals(true, tls.getJSONObject("reality").getBoolean("enabled"))
        assertEquals(true, tls.getJSONObject("utls").getBoolean("enabled"))
        assertEquals("chrome", tls.getJSONObject("utls").getString("fingerprint"))
        assertEquals("proxy", config.getJSONObject("route").getString("final"))
    }

    @Test
    fun `keeps standard base64 Reality keys and accepts public key alias`() {
        val node = importedNode(
            "vless://123e4567-e89b-12d3-a456-426614174000@edge.example.com:443" +
                "?security=reality&serverName=cdn.example.com&publicKey=abc+def&shortId=abcd&type=tcp#Edge",
        )

        val outbound = JSONObject(SingBoxConfigurationBuilder().build(node, localPort = 2080))
            .getJSONArray("outbounds")
            .getJSONObject(0)

        val tls = outbound.getJSONObject("tls")
        assertEquals("cdn.example.com", tls.getString("server_name"))
        assertEquals("abc+def", tls.getJSONObject("reality").getString("public_key"))
        assertEquals("abcd", tls.getJSONObject("reality").getString("short_id"))
    }

    @Test
    fun `uses the Android local DNS transport to resolve an outbound domain`() {
        val node = importedNode(
            "vless://123e4567-e89b-12d3-a456-426614174000@edge.example.com:443" +
                "?security=tls&sni=cdn.example.com&type=ws&path=%2Fsocket#Edge",
        )

        val config = JSONObject(SingBoxConfigurationBuilder().build(node, localPort = 2080))

        val dnsServer = config.getJSONObject("dns").getJSONArray("servers").getJSONObject(0)
        assertEquals("local", dnsServer.getString("type"))
        assertEquals("system", dnsServer.getString("tag"))
        assertEquals("system", config.getJSONObject("route").getString("default_domain_resolver"))
    }

    private fun importedNode(uri: String) =
        (ProxySubscriptionParser(::decodeWithJvmBase64).parse(encode(uri)) as SubscriptionImportResult.Imported).nodes.single()

    private fun encode(value: String): String = Base64.getEncoder().encodeToString(value.toByteArray())

    private fun decodeWithJvmBase64(encoded: String, urlSafe: Boolean): ByteArray? = runCatching {
        if (urlSafe) {
            Base64.getUrlDecoder().decode(encoded)
        } else {
            Base64.getDecoder().decode(encoded)
        }
    }.getOrNull()
}

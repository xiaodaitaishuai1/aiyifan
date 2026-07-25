package com.aiyifan.app.feature.proxy.runtime

import com.aiyifan.app.feature.proxy.domain.ProxyNode
import com.aiyifan.app.feature.proxy.domain.ProxyProtocol
import java.net.URI
import java.net.URLDecoder
import org.json.JSONArray
import org.json.JSONObject

interface SingBoxConfigProvider {
    fun build(node: ProxyNode, localPort: Int): String
}

class SingBoxConfigurationBuilder : SingBoxConfigProvider {

    override fun build(node: ProxyNode, localPort: Int): String {
        require(localPort in 1..65535) { "Invalid local proxy port" }
        require(node.protocol == ProxyProtocol.VLESS) { "This node protocol is not connectable yet" }

        val uri = URI(node.serializedNode)
        val query = uri.queryParameters()
        val outbound = JSONObject()
            .put("type", "vless")
            .put("tag", PROXY_TAG)
            .put("server", node.host)
            .put("server_port", node.port)
            .put("uuid", uri.userInfo)

        query["flow"]?.takeIf(String::isNotBlank)?.let { outbound.put("flow", it) }
        addTransport(outbound, query)
        addTls(outbound, node.host, query)

        return JSONObject()
            .put("log", JSONObject().put("level", "warn"))
            .put(
                "inbounds",
                JSONArray().put(
                    JSONObject()
                        .put("type", "mixed")
                        .put("tag", "local")
                        .put("listen", LOOPBACK_ADDRESS)
                        .put("listen_port", localPort),
                ),
            )
            .put(
                "outbounds",
                JSONArray()
                    .put(outbound)
                    .put(JSONObject().put("type", "direct").put("tag", "direct")),
            )
            .put(
                "dns",
                JSONObject().put(
                    "servers",
                    JSONArray().put(
                        JSONObject()
                            .put("type", "local")
                            .put("tag", SYSTEM_DNS_TAG),
                    ),
                ),
            )
            .put(
                "route",
                JSONObject()
                    .put("final", PROXY_TAG)
                    .put("default_domain_resolver", SYSTEM_DNS_TAG),
            )
            .toString()
    }

    private fun addTransport(outbound: JSONObject, query: Map<String, String>) {
        when (query["type"]?.lowercase()) {
            null,
            "",
            "tcp",
            -> Unit

            "ws" -> {
                val transport = JSONObject().put("type", "ws")
                query["path"]?.let { transport.put("path", it) }
                query["host"]?.let { host ->
                    transport.put("headers", JSONObject().put("Host", host))
                }
                outbound.put("transport", transport)
            }

            "grpc" -> {
                val transport = JSONObject().put("type", "grpc")
                query["serviceName"]?.let { transport.put("service_name", it) }
                outbound.put("transport", transport)
            }

            else -> throw IllegalArgumentException("Unsupported VLESS transport")
        }
    }

    private fun addTls(outbound: JSONObject, host: String, query: Map<String, String>) {
        val security = query["security"]?.lowercase()
        if (security.isNullOrBlank() || security == "none") return

        val tls = JSONObject()
            .put("enabled", true)
            .put("server_name", query.firstNonBlank("sni", "serverName", "server_name") ?: host)
        if (query["insecure"].asBoolean()) tls.put("insecure", true)
        query["alpn"]?.split(',')?.filter(String::isNotBlank)?.takeIf(List<String>::isNotEmpty)?.let { protocols ->
            tls.put("alpn", JSONArray(protocols))
        }
        if (security == "reality") {
            val reality = JSONObject().put("enabled", true)
            query.firstNonBlank("pbk", "publicKey", "public_key")?.let { reality.put("public_key", it) }
            query.firstNonBlank("sid", "shortId", "short_id")?.let { reality.put("short_id", it) }
            tls.put("reality", reality)
        }
        query["fp"]?.takeIf(String::isNotBlank)?.let { fingerprint ->
            tls.put(
                "utls",
                JSONObject()
                    .put("enabled", true)
                    .put("fingerprint", fingerprint),
            )
        }
        outbound.put("tls", tls)
    }

    private fun URI.queryParameters(): Map<String, String> =
        rawQuery
            ?.split('&')
            ?.mapNotNull { part ->
                val separator = part.indexOf('=')
                if (separator < 0) {
                    null
                } else {
                    decode(part.substring(0, separator)) to decode(part.substring(separator + 1))
                }
            }
            ?.toMap()
            .orEmpty()

    private fun Map<String, String>.firstNonBlank(vararg names: String): String? =
        names.firstNotNullOfOrNull { name -> get(name)?.takeIf(String::isNotBlank) }

    private fun String?.asBoolean(): Boolean = this == "1" || equals("true", ignoreCase = true)

    // VLESS URIs commonly contain standard Base64 values. A literal '+' is data, not form encoding.
    private fun decode(value: String): String = URLDecoder.decode(value.replace("+", "%2B"), Charsets.UTF_8.name())

    private companion object {
        const val LOOPBACK_ADDRESS = "127.0.0.1"
        const val PROXY_TAG = "proxy"
        const val SYSTEM_DNS_TAG = "system"
    }
}

package com.aiyifan.app.core.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URLEncoder
import java.net.URL

private const val DEFAULT_REGION = "cn"

data class HttpResponse(
    val code: Int,
    val body: String,
)

interface HttpFetcher {
    suspend fun get(url: String): HttpResponse

    suspend fun getDirect(url: String): HttpResponse = get(url)

    suspend fun postForm(url: String, params: Map<String, String>): HttpResponse =
        throw UnsupportedOperationException("Form POST is not supported")

    suspend fun postJson(url: String, body: String): HttpResponse =
        throw UnsupportedOperationException("JSON POST is not supported")
}

fun interface HttpConnectionOpener {
    fun open(url: URL, proxy: Proxy?): HttpURLConnection
}

class UrlConnectionHttpFetcher(
    private val endpointProvider: () -> LocalProxyEndpoint? = { null },
    private val connectionOpener: HttpConnectionOpener = HttpConnectionOpener { url, proxy ->
        (proxy?.let(url::openConnection) ?: url.openConnection()) as HttpURLConnection
    },
) : HttpFetcher {
    override suspend fun get(url: String): HttpResponse =
        get(url, ProxyConnectionPolicy.select(endpointProvider()))

    override suspend fun getDirect(url: String): HttpResponse =
        get(url, proxy = null)

    private suspend fun get(url: String, proxy: Proxy?): HttpResponse =
        withContext(Dispatchers.IO) {
            val connection = connectionOpener.open(URL(url), proxy).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Aiyifan/1.0")
                setRequestProperty("Accept", "application/json,text/plain,*/*")
                setRequestProperty("Accept-Language", "zh-CN")
                setRequestProperty("X-Region", DEFAULT_REGION)
            }
            try {
                val stream = if (connection.responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }
                val body = stream?.use { input ->
                    BufferedReader(InputStreamReader(input)).readText()
                }.orEmpty()
                HttpResponse(connection.responseCode, body)
            } finally {
                connection.disconnect()
            }
        }

    override suspend fun postForm(url: String, params: Map<String, String>): HttpResponse =
        withContext(Dispatchers.IO) {
            val connection = connectionOpener.open(
                URL(url),
                ProxyConnectionPolicy.select(endpointProvider()),
            ).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 10_000
                instanceFollowRedirects = true
                doOutput = true
                setRequestProperty("User-Agent", "Aiyifan/1.0")
                setRequestProperty("Accept", "application/json,text/plain,*/*")
                setRequestProperty("Accept-Language", "zh-CN")
                setRequestProperty("X-Region", DEFAULT_REGION)
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            }
            try {
                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(
                        params.entries.joinToString("&") { (key, value) ->
                            "${encode(key)}=${encode(value)}"
                        },
                    )
                }
                val stream = if (connection.responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }
                val body = stream?.use { input ->
                    BufferedReader(InputStreamReader(input)).readText()
                }.orEmpty()
                HttpResponse(connection.responseCode, body)
            } finally {
                connection.disconnect()
            }
        }

    override suspend fun postJson(url: String, body: String): HttpResponse =
        withContext(Dispatchers.IO) {
            val connection = connectionOpener.open(
                URL(url),
                ProxyConnectionPolicy.select(endpointProvider()),
            ).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 10_000
                instanceFollowRedirects = true
                doOutput = true
                setRequestProperty("User-Agent", "Aiyifan/1.0")
                setRequestProperty("Accept", "application/json,text/plain,*/*")
                setRequestProperty("Accept-Language", "zh-CN")
                setRequestProperty("X-Region", DEFAULT_REGION)
                setRequestProperty("BundleId", "com.cqcsy.ifvod")
                setRequestProperty("Version", "V3")
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
            try {
                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(body)
                }
                val stream = if (connection.responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }
                val responseBody = stream?.use { input ->
                    BufferedReader(InputStreamReader(input)).readText()
                }.orEmpty()
                HttpResponse(connection.responseCode, responseBody)
            } finally {
                connection.disconnect()
            }
        }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
}

class RemoteConfigResolver(
    private val fetcher: HttpFetcher,
) {
    suspend fun resolveBaseUrl(): String {
        // V1 serves the mainland China catalog by default. The public config currently
        // has no region-specific cn file, so the default config is the cn source.
        for (url in listOf(configUrlFor(DEFAULT_REGION))) {
            val api = fetchConfigApi(url)
            if (!api.isNullOrBlank()) {
                return ensureTrailingSlash(api)
            }
        }
        return DEFAULT_BASE_URL
    }

    private fun configUrlFor(region: String): String =
        if (region.equals(DEFAULT_REGION, ignoreCase = true)) DEFAULT_CONFIG_URL
        else "https://dataexbbff.github.io/rawApp${region.uppercase()}.json"

    private suspend fun fetchConfigApi(url: String): String? {
        val response = fetcher.get(url)
        if (response.code !in 200..299) return null
        return runCatching {
            JSONObject(response.body).optString("api").trim()
        }.getOrNull().takeUnless { it.isNullOrBlank() }
    }

    private fun ensureTrailingSlash(url: String): String =
        if (url.endsWith("/")) url else "$url/"

    companion object {
        private const val DEFAULT_CONFIG_URL = "https://dataexbbff.github.io/rawApp.json"
        private const val DEFAULT_BASE_URL = "https://api.tripdata.app/"
    }
}

package com.aiyifan.app.core.data.baipiaozhe

/**
 * HTTP headers required to retrieve the resolved playback stream (and its
 * encrypted-key / HLS segments) without being rejected by the CDN.
 *
 * These mirror what the page sends when it resolves the stream; the login cookie
 * remains inside the WebView and is only surfaced here for the media requests.
 */
data class PlaybackHttpHeaders(
    val referer: String? = null,
    val userAgent: String? = null,
    val cookie: String? = null,
) {
    fun applyTo(configure: (header: String, value: String) -> Unit) {
        referer?.takeIf(String::isNotBlank)?.let { configure("Referer", it) }
        userAgent?.takeIf(String::isNotBlank)?.let { configure("User-Agent", it) }
        cookie?.takeIf(String::isNotBlank)?.let { configure("Cookie", it) }
    }

    fun isEmpty(): Boolean = referer.isNullOrBlank() && userAgent.isNullOrBlank() && cookie.isNullOrBlank()

    val entries: Map<String, String>
        get() = buildMap {
            referer?.takeIf(String::isNotBlank)?.let { put("Referer", it) }
            userAgent?.takeIf(String::isNotBlank)?.let { put("User-Agent", it) }
            cookie?.takeIf(String::isNotBlank)?.let { put("Cookie", it) }
        }
}

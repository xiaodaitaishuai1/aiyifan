package com.aiyifan.app.core.data.baipiaozhe

import com.aiyifan.app.core.model.Episode
import org.json.JSONObject
import java.net.URI

/**
 * Normalizes the playback payload handed over from the embedded baipiaozhe WebView
 * into an [Episode] ready for the existing player.
 *
 * The page resolves a playable stream (m3u8/MP4) and posts it through the injected
 * JS bridge; this keeps the login session inside the WebView and lets the native
 * player consume only the final playback URL.
 */
object BaipiaozhePlaybackPayload {

    /**
     * Parses a payload string captured from the WebView bridge.
     *
     * Accepts either a bare URL or a JSON object carrying `play_url` (also handled
     * aliases: `url`, `src`, `playUrl`) plus optional `title` / `episode`.
     */
    fun parse(raw: String?): Episode? = raw?.trim().takeUnless { it.isNullOrBlank() }?.let { input ->
        val (url, title, episodeTitle) = extract(input)
        val mediaUrl = url?.trim().orEmpty()
        if (mediaUrl.isBlank()) return null
        if (!isPlayable(mediaUrl)) return null
        Episode(
            episodeKey = "baipiaozhe-${mediaUrl.hashCode()}",
            episodeTitle = title ?: episodeTitle ?: DEFAULT_TITLE,
            uniqueId = mediaUrl.hashCode(),
            mediaUrl = mediaUrl,
        )
    }

    private fun extract(input: String): Triple<String?, String?, String?> {
        return runCatching { JSONObject(input) }.getOrNull()?.let { json ->
            Triple(
                firstUrl(
                    json.optString("play_url"),
                    json.optString("playUrl"),
                    json.optString("url"),
                    json.optString("src"),
                ),
                json.optString("title").trim().takeIf(String::isNotBlank),
                json.optString("episode").trim().takeIf(String::isNotBlank),
            )
        } ?: Triple(input.trim(), null, null)
    }

    private fun firstUrl(vararg candidates: String): String =
        candidates.firstOrNull { it.isNotBlank() }.orEmpty()

    private fun isPlayable(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank()) return false

        val path = uri.path.orEmpty().lowercase()
        return path.endsWith(".m3u8") || path.endsWith(".mp4")
    }

    private const val DEFAULT_TITLE = ""
}

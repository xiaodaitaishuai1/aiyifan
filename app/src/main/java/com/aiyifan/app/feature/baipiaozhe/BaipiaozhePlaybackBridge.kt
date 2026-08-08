package com.aiyifan.app.feature.baipiaozhe

import android.webkit.JavascriptInterface
import com.aiyifan.app.core.data.AppGraph
import com.aiyifan.app.core.data.baipiaozhe.BaipiaozhePlaybackPayload
import com.aiyifan.app.core.data.baipiaozhe.PlaybackHttpHeaders

/**
 * Native bridge installed into the embedded baipiaozhe WebView. The injected page
 * script calls [onPlaybackResolved] with the final playable stream so the native
 * player can take over playback.
 */
class BaipiaozhePlaybackBridge(
    private val onUnavailable: () -> Unit = {},
    private val dispatchToMain: ((() -> Unit) -> Unit),
    private val isTrustedPage: () -> Boolean,
    private val headersForPlaybackUrl: (String) -> PlaybackHttpHeaders = { PlaybackHttpHeaders() },
    private val onPlaybackReady: (String, String?) -> Unit,
) {
    @JavascriptInterface
    fun onPlaybackResolved(payload: String) {
        dispatchToMain { resolvePlayback(payload) }
    }

    private fun resolvePlayback(payload: String) {
        val episode = BaipiaozhePlaybackPayload.parse(payload)
        val mediaUrl = episode?.mediaUrl
        if (!isTrustedPage() || mediaUrl == null) {
            onUnavailable()
            return
        }
        AppGraph.playbackHeadersHolder.update(headersForPlaybackUrl(mediaUrl))
        val title = episode.episodeTitle.takeIf(String::isNotBlank)
        onPlaybackReady(mediaUrl, title)
    }
}

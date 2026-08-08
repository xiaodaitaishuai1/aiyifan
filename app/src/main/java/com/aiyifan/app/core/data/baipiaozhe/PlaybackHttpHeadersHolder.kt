package com.aiyifan.app.core.data.baipiaozhe

/**
 * Thread-safe holder for the HTTP headers used by the active baipiaozhe playback
 * session. The WebView bridge sets these right before handing a resolved URL to
 * the native player, and the player data source reads them per media request.
 */
class PlaybackHttpHeadersHolder {
    @Volatile
    private var headers: PlaybackHttpHeaders = PlaybackHttpHeaders()

    fun update(newHeaders: PlaybackHttpHeaders) {
        headers = newHeaders
    }

    fun clear() {
        headers = PlaybackHttpHeaders()
    }

    fun current(): PlaybackHttpHeaders = headers
}

package com.aiyifan.app.core.ui

class PlaybackScreenAwakeController(
    private val setKeepScreenOn: (Boolean) -> Unit,
) {
    private var attached = false
    private var isPlaying = false

    fun attach(isPlaying: Boolean) {
        this.isPlaying = isPlaying
        attached = true
        updateScreenAwake()
    }

    fun onIsPlayingChanged(isPlaying: Boolean) {
        this.isPlaying = isPlaying
        if (attached) updateScreenAwake()
    }

    fun detach() {
        if (!attached) return
        attached = false
        updateScreenAwake()
    }

    private fun updateScreenAwake() {
        setKeepScreenOn(attached && isPlaying)
    }
}

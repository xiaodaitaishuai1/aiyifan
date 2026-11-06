package com.aiyifan.app.feature.video

enum class SleepTimerOption(val durationMs: Long?) {
    OFF(null), MINUTES_15(900_000L), MINUTES_30(1_800_000L), MINUTES_60(3_600_000L), END_OF_EPISODE(null),
}

data class SleepTimerState(val option: SleepTimerOption, val remainingMs: Long? = null)

/** Ephemeral monotonic deadline; intentionally never persisted with playback history. */
class SleepTimer(private val clock: () -> Long) {
    private var option = SleepTimerOption.OFF
    private var deadline: Long? = null
    private var episodeIdentity: Pair<String, String>? = null

    val state: SleepTimerState
        get() = SleepTimerState(option, deadline?.let { (it - clock()).coerceAtLeast(0L) })

    fun set(option: SleepTimerOption, mediaKey: String?, episodeKey: String?) {
        clear()
        if (option == SleepTimerOption.END_OF_EPISODE && (mediaKey == null || episodeKey == null)) return
        this.option = option
        deadline = option.durationMs?.let { clock() + it }
        if (option == SleepTimerOption.END_OF_EPISODE) episodeIdentity = mediaKey!! to episodeKey!!
    }

    fun onSelection(mediaKey: String, episodeKey: String?) {
        val bound = episodeIdentity ?: return
        if (bound.first != mediaKey || episodeKey != null && bound.second != episodeKey) clear()
    }

    fun consumeExpiry(mediaKey: String?, episodeKey: String?, ended: Boolean): Boolean {
        val expired = deadline?.let { clock() >= it } == true ||
            (ended && episodeIdentity != null && episodeIdentity == (mediaKey to episodeKey))
        if (expired) clear()
        return expired
    }

    fun clear() {
        option = SleepTimerOption.OFF
        deadline = null
        episodeIdentity = null
    }
}

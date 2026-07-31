package com.aiyifan.app.feature.video

object AutoSkipPolicy {
    fun initialPositionMs(
        resumePositionMs: Long,
        introSecond: Long?,
        enabled: Boolean,
    ): Long =
        resumePositionMs.takeIf { it > 0L }
            ?: introSecond?.toMillisecondsOrNull(enabled)
            ?: 0L

    fun shouldAdvance(
        positionMs: Long,
        outroSecond: Long?,
        enabled: Boolean,
        hasNextEpisode: Boolean,
        hasAdvanced: Boolean,
    ): Boolean =
        enabled &&
            hasNextEpisode &&
            !hasAdvanced &&
            outroSecond?.toMillisecondsOrNull(enabled)?.let { positionMs >= it } == true

    private fun Long.toMillisecondsOrNull(enabled: Boolean): Long? =
        takeIf { enabled && it in 1L..MAX_SAFE_SECOND }?.times(MILLIS_PER_SECOND)

    private const val MILLIS_PER_SECOND = 1_000L
    private const val MAX_SAFE_SECOND = Long.MAX_VALUE / MILLIS_PER_SECOND
}

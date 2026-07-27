package com.aiyifan.app.feature.video

import com.aiyifan.app.core.model.PlaybackQuality

object PlaybackQualitySelector {
    fun select(
        qualities: List<PlaybackQuality>,
        sessionResolution: String?,
        episodeResolution: String?,
    ): PlaybackQuality? {
        val usable = qualities.filter { it.resolution.isNotBlank() }
        return usable.firstOrNull { it.resolution == sessionResolution }
            ?: usable.firstOrNull(PlaybackQuality::isDefault)
            ?: usable.firstOrNull { it.resolution == episodeResolution }
            ?: usable.firstOrNull()
    }
}

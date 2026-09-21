package com.aiyifan.app.feature.video

import com.aiyifan.app.core.model.Episode
import com.aiyifan.app.core.model.VideoDetail
import com.aiyifan.app.core.model.WatchHistory

data class PlaybackResumeTarget(val episode: Episode, val positionMs: Long)

object PlaybackResumePolicy {
    fun target(detail: VideoDetail, history: WatchHistory?): PlaybackResumeTarget? {
        val saved = history?.takeIf { it.mediaKey == detail.mediaKey }
        val episode = detail.episodes.firstOrNull { it.episodeKey == saved?.episodeKey }
        if (episode == null) return detail.defaultEpisode?.let { PlaybackResumeTarget(it, 0L) }
        val position = saved!!.progressMs.coerceAtLeast(0L).let {
            if (saved.durationMs > 0 && it >= saved.durationMs) 0L else it
        }
        return PlaybackResumeTarget(episode, position)
    }
}

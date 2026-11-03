package com.aiyifan.app.feature.video

import com.aiyifan.app.core.model.Episode
import com.aiyifan.app.core.model.VideoDetail

object PlayerEpisodePresentation {
    fun title(detail: VideoDetail, episode: Episode?, format: String): String {
        val index = detail.episodes.indexOfFirst { it.episodeKey == episode?.episodeKey }
        return if (index >= 0) format.format(detail.title, index + 1) else detail.title
    }
}

package com.aiyifan.app.feature.localmedia

import com.aiyifan.app.feature.localmedia.model.LocalVideo

object LocalMediaLibraryPolicy {
    fun validVideos(videos: List<LocalVideo>): List<LocalVideo> = videos.filter { it.durationMs > 0 }

    fun search(videos: List<LocalVideo>, query: String): List<LocalVideo> {
        val needle = query.trim()
        if (needle.isEmpty()) return videos
        return videos.filter { it.displayName.contains(needle, ignoreCase = true) }
    }
}

package com.aiyifan.app.core.model

data class Category(
    val id: String,
    val name: String,
    val type: Int,
    val styleType: Int,
    val url: String? = null,
)

data class VideoSummary(
    val mediaKey: String,
    val episodeKey: String? = null,
    val title: String,
    val coverUrl: String,
    val videoType: Int,
    val contentType: String? = null,
    val mediaType: String? = null,
    val score: String? = null,
    val playCount: Int = 0,
    val updateStatus: String? = null,
    val duration: String? = null,
    val year: String? = null,
    val area: String? = null,
    val actor: String? = null,
    val director: String? = null,
    val episodePreviews: List<Episode> = emptyList(),
)

data class SearchSuggestion(
    val keyword: String,
)

data class Episode(
    val episodeKey: String,
    val episodeTitle: String,
    val uniqueId: Int,
    val mediaUrl: String?,
    val resolution: String? = null,
    val lang: String? = null,
    val duration: String? = null,
    val watchProgressMs: Long = 0L,
)

data class PlaybackQuality(
    val resolution: String,
    val description: String,
    val mediaUrl: String,
    val isDefault: Boolean = false,
)

data class ResolvedPlayback(
    val episode: Episode,
    val qualities: List<PlaybackQuality> = emptyList(),
)

data class PlaybackLanguage(
    val mediaKey: String,
    val name: String,
)

data class Comment(
    val id: Long,
    val content: String,
    val userName: String,
    val avatarUrl: String? = null,
    val postTime: String,
    val likeCount: Int = 0,
    val liked: Boolean = false,
    val replies: List<Comment> = emptyList(),
)

data class VideoDetail(
    val mediaKey: String,
    val title: String,
    val coverUrl: String,
    val videoType: Int,
    val typeName: String? = null,
    val director: String? = null,
    val actor: String? = null,
    val introduce: String? = null,
    val playCount: Int = 0,
    val comments: Int = 0,
    val shareCount: Int = 0,
    val publishTime: String? = null,
    val updateMsg: String? = null,
    val commentEnabled: Boolean = true,
    val episodes: List<Episode> = emptyList(),
    val qualities: List<PlaybackQuality> = emptyList(),
    val languages: List<PlaybackLanguage> = emptyList(),
    val related: List<VideoSummary> = emptyList(),
) {
    val defaultEpisode: Episode?
        get() = episodes.firstOrNull()
}

data class WatchHistory(
    val mediaKey: String,
    val episodeKey: String,
    val title: String,
    val coverUrl: String,
    val videoType: Int,
    val progressMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
)

data class FavoriteVideo(
    val mediaKey: String,
    val title: String,
    val coverUrl: String,
    val videoType: Int,
    val createdAt: Long,
)

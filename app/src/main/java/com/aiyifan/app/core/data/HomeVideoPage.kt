package com.aiyifan.app.core.data

import com.aiyifan.app.core.model.VideoSummary

data class HomeVideoPage(
    val videos: List<VideoSummary>,
    val hasMore: Boolean,
)

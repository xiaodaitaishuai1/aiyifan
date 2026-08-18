package com.aiyifan.app.feature.localmedia.model

data class LocalPlaybackRecord(
    val mediaStoreId: Long,
    val contentUri: String,
    val displayName: String,
    val durationMs: Long,
    val positionMs: Long,
    val updatedAt: Long,
)

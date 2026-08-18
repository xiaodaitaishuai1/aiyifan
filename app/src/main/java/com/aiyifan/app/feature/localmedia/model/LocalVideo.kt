package com.aiyifan.app.feature.localmedia.model

data class LocalVideo(
    val id: Long,
    val contentUri: String,
    val displayName: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val dateAddedMs: Long,
    val bucketName: String?,
)

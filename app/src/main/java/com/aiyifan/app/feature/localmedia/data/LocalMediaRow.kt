package com.aiyifan.app.feature.localmedia.data

data class LocalMediaRow(
    val id: Long,
    val displayName: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val dateAddedSeconds: Long,
    val bucketName: String?,
)
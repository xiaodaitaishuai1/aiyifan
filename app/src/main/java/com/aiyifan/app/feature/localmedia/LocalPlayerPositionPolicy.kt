package com.aiyifan.app.feature.localmedia

import com.aiyifan.app.feature.localmedia.model.LocalPlaybackRecord

object LocalPlayerPositionPolicy {
    val speedOptions: List<Float> = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

    fun contentUriFrom(value: String?): String {
        val trimmed = value?.trim().orEmpty()
        if (!trimmed.startsWith("content://") || trimmed.length <= "content://".length) return ""
        return trimmed
    }

    fun clampPosition(positionMs: Long, durationMs: Long): Long {
        if (positionMs <= 0L || durationMs <= 0L || positionMs >= durationMs) return 0L
        return positionMs
    }

    fun recordFor(
        mediaStoreId: Long,
        contentUri: String,
        displayName: String,
        durationMs: Long,
        positionMs: Long,
        updatedAt: Long,
    ): LocalPlaybackRecord = LocalPlaybackRecord(
        mediaStoreId = mediaStoreId,
        contentUri = contentUri,
        displayName = displayName,
        durationMs = durationMs,
        positionMs = positionMs,
        updatedAt = updatedAt,
    )
}
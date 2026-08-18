package com.aiyifan.app.feature.localmedia

import com.aiyifan.app.feature.localmedia.model.LocalPlaybackRecord

object LocalPlaybackStorePolicy {
    const val MAX_RECORDS = 100

    fun merge(
        current: List<LocalPlaybackRecord>,
        incoming: LocalPlaybackRecord,
    ): List<LocalPlaybackRecord> {
        val withoutIncoming = current.filterNot { it.mediaStoreId == incoming.mediaStoreId }
        return (withoutIncoming + incoming)
            .sortedByDescending(LocalPlaybackRecord::updatedAt)
            .take(MAX_RECORDS)
    }

    fun prune(
        records: List<LocalPlaybackRecord>,
        availableIds: Set<Long>,
    ): List<LocalPlaybackRecord> = records.filter { it.mediaStoreId in availableIds }

    fun resumePosition(positionMs: Long, durationMs: Long): Long {
        if (positionMs <= 0 || durationMs <= 0 || positionMs >= durationMs) return 0
        return positionMs
    }
}

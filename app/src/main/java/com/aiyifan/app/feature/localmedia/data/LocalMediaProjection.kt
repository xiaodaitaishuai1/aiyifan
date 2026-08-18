package com.aiyifan.app.feature.localmedia.data

import com.aiyifan.app.feature.localmedia.model.LocalVideo

object LocalMediaProjection {
    val columns: List<String> = listOf(
        "_id",
        "_display_name",
        "duration",
        "_size",
        "date_added",
        "bucket_display_name",
    )

    fun map(
        row: LocalMediaRow,
        contentUri: String,
    ): LocalVideo? {
        if (row.durationMs <= 0L) return null
        return LocalVideo(
            id = row.id,
            contentUri = contentUri,
            displayName = row.displayName,
            durationMs = row.durationMs,
            sizeBytes = row.sizeBytes,
            dateAddedMs = row.dateAddedSeconds * 1_000L,
            bucketName = row.bucketName,
        )
    }
}
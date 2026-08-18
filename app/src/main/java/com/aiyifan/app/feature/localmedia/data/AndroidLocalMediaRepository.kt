package com.aiyifan.app.feature.localmedia.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.aiyifan.app.feature.localmedia.model.LocalVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidLocalMediaRepository(
    private val context: Context,
) : LocalMediaRepository {

    override suspend fun queryVideos(): List<LocalVideo> = withContext(Dispatchers.IO) {
        val contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val videos = mutableListOf<LocalVideo>()

        context.contentResolver.query(
            contentUri,
            LocalMediaProjection.columns.toTypedArray(),
            null,
            null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC",
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow("_id")
            val nameColumn = cursor.getColumnIndexOrThrow("_display_name")
            val durationColumn = cursor.getColumnIndexOrThrow("duration")
            val sizeColumn = cursor.getColumnIndexOrThrow("_size")
            val dateAddedColumn = cursor.getColumnIndexOrThrow("date_added")
            val bucketColumn = cursor.getColumnIndexOrThrow("bucket_display_name")

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val mapped = LocalMediaProjection.map(
                    row = LocalMediaRow(
                        id = id,
                        displayName = cursor.getString(nameColumn) ?: "",
                        durationMs = cursor.getLong(durationColumn),
                        sizeBytes = cursor.getLong(sizeColumn),
                        dateAddedSeconds = cursor.getLong(dateAddedColumn),
                        bucketName = cursor.getString(bucketColumn),
                    ),
                    contentUri = ContentUris.withAppendedId(contentUri, id).toString(),
                )
                if (mapped != null) videos += mapped
            }
        }

        videos
    }
}
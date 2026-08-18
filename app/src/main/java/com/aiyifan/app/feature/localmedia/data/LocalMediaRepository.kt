package com.aiyifan.app.feature.localmedia.data

import com.aiyifan.app.feature.localmedia.model.LocalVideo

interface LocalMediaRepository {
    suspend fun queryVideos(): List<LocalVideo>
}
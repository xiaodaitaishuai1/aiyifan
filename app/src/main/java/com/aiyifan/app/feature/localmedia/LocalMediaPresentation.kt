package com.aiyifan.app.feature.localmedia

object LocalMediaPresentation {
    fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0L) return ""
        val totalSeconds = durationMs / 1_000L
        val hours = totalSeconds / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }

    fun formatSize(sizeBytes: Long): String {
        if (sizeBytes <= 0L) return ""
        val units = listOf("B", "KB", "MB", "GB", "TB")
        var value = sizeBytes.toDouble()
        var unitIndex = 0
        while (value >= 1024.0 && unitIndex < units.lastIndex) {
            value /= 1024.0
            unitIndex++
        }
        return if (unitIndex == 0) {
            "${sizeBytes} B"
        } else {
            "%.1f %s".format(value, units[unitIndex])
        }
    }
}
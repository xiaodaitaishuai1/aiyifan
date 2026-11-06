package com.aiyifan.app.feature.history

import java.util.Locale

object WatchProgressPresentation {
    fun time(progressMs: Long, durationMs: Long): String =
        if (durationMs > 0) "${format(progressMs)} / ${format(durationMs)}" else format(progressMs)

    fun format(milliseconds: Long): String {
        val seconds = milliseconds.coerceAtLeast(0) / 1000
        return if (seconds >= 3600) String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
        else String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
    }

    fun percent(progressMs: Long, durationMs: Long): Int =
        if (durationMs > 0) (progressMs.toDouble() / durationMs * 100).coerceIn(0.0, 100.0).toInt() else 0

    fun completed(progressMs: Long, durationMs: Long): Boolean = durationMs > 0 && progressMs >= durationMs
}

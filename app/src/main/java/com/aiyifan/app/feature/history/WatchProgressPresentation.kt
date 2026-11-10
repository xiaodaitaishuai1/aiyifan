package com.aiyifan.app.feature.history

import java.util.Locale

object WatchProgressPresentation {
    fun time(progressMs: Long, durationMs: Long): String =
        if (durationMs > 0) "${format(progressMs)} / ${format(durationMs)}" else format(progressMs)

    fun compactTime(progressMs: Long, durationMs: Long): String =
        if (durationMs > 0) "${compactFormat(progressMs, roundUp = false)} / ${compactFormat(durationMs, roundUp = true)}"
        else compactFormat(progressMs, roundUp = false)

    private fun compactFormat(milliseconds: Long, roundUp: Boolean): String {
        val seconds = milliseconds.coerceAtLeast(0) / 1000
        val minutes = if (roundUp) (seconds + 59) / 60 else seconds / 60
        return if (minutes >= 60) String.format(Locale.ROOT, "%d:%02d", minutes / 60, minutes % 60)
        else String.format(Locale.ROOT, "%d:%02d", minutes, 0)
    }

    fun format(milliseconds: Long): String {
        val seconds = milliseconds.coerceAtLeast(0) / 1000
        return if (seconds >= 3600) String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
        else String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
    }

    fun percent(progressMs: Long, durationMs: Long): Int =
        if (durationMs > 0) (progressMs.toDouble() / durationMs * 100).coerceIn(0.0, 100.0).toInt() else 0

    fun completed(progressMs: Long, durationMs: Long): Boolean = durationMs > 0 && progressMs >= durationMs
}

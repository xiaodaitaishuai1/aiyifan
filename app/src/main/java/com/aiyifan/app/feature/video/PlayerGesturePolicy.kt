package com.aiyifan.app.feature.video

enum class PlayerGestureKind {
    BRIGHTNESS,
    VOLUME,
}

object PlayerGesturePolicy {
    fun brightnessPercent(windowBrightness: Float, systemBrightness: Int): Int =
        (if (windowBrightness >= 0f) (windowBrightness * 100).toInt()
        else systemBrightness.coerceIn(0, 255) * 100 / 255).coerceIn(1, 100)

    const val LONG_PRESS_MS = 2_000L
    private const val MAX_SEEK_OFFSET_MS = 120_000L

    fun kindFor(downX: Float, width: Int): PlayerGestureKind =
        if (downX < width / 2f) PlayerGestureKind.BRIGHTNESS else PlayerGestureKind.VOLUME

    fun adjustVertical(start: Int, deltaY: Float, height: Int, min: Int, max: Int): Int =
        (start + (-deltaY / height.coerceAtLeast(1) * (max - min)).toInt()).coerceIn(min, max)

    fun isSeekLongPress(elapsedMs: Long, movedPx: Float, touchSlopPx: Int): Boolean =
        elapsedMs >= LONG_PRESS_MS && movedPx <= touchSlopPx

    fun seekPreview(startMs: Long, deltaX: Float, width: Int, durationMs: Long): Long =
        (startMs + (deltaX / width.coerceAtLeast(1) * MAX_SEEK_OFFSET_MS).toLong())
            .coerceIn(0L, durationMs.coerceAtLeast(0L))
}

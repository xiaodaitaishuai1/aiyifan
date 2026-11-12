package com.aiyifan.app.feature.mine

/** Three small box passes approximate a Gaussian blur on a 1/8-size thumbnail. */
internal object HeaderBlur {
    fun blur(pixels: IntArray, width: Int, height: Int): IntArray {
        require(width > 0 && height > 0 && pixels.size == width * height)
        var source = pixels.copyOf()
        var destination = IntArray(pixels.size)
        repeat(3) {
            for (horizontal in listOf(true, false)) {
                for (y in 0 until height) {
                    if (Thread.currentThread().isInterrupted) return source
                    for (x in 0 until width) {
                        var red = 0
                        var green = 0
                        var blue = 0
                        for (offset in -3..3) {
                            val sx = if (horizontal) (x + offset).coerceIn(0, width - 1) else x
                            val sy = if (horizontal) y else (y + offset).coerceIn(0, height - 1)
                            val color = source[sy * width + sx]
                            red += color shr 16 and 255
                            green += color shr 8 and 255
                            blue += color and 255
                        }
                        destination[y * width + x] = (255 shl 24) or (red / 7 shl 16) or
                            (green / 7 shl 8) or (blue / 7)
                    }
                }
                val previous = source
                source = destination
                destination = previous
            }
        }
        return source
    }
}

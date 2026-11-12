package com.aiyifan.app.feature.mine

import org.junit.Assert.*
import org.junit.Test

class HeaderBlurTest {
    @Test fun `uniform color stays uniform including edges`() {
        val pixels = IntArray(35) { 0xff123456.toInt() }
        assertArrayEquals(pixels, HeaderBlur.blur(pixels, 7, 5))
    }

    @Test fun `blur spreads a bright feature without changing input`() {
        val pixels = IntArray(81) { 0xff000000.toInt() }
        pixels[40] = 0xffffffff.toInt()
        val result = HeaderBlur.blur(pixels, 9, 9)
        assertEquals(0xffffffff.toInt(), pixels[40])
        assertTrue((result[40] and 255) in 1..254)
        assertTrue((result[39] and 255) > 0)
    }

    @Test fun `single pixel and narrow images are supported`() {
        assertArrayEquals(intArrayOf(-1), HeaderBlur.blur(intArrayOf(-1), 1, 1))
        assertEquals(7, HeaderBlur.blur(IntArray(7) { -1 }, 1, 7).size)
    }
}

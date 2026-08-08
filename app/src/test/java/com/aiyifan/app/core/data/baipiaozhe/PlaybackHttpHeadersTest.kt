package com.aiyifan.app.core.data.baipiaozhe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackHttpHeadersTest {

    @Test
    fun `applies only non blank headers`() {
        val headers = PlaybackHttpHeaders(
            referer = "https://baipiaozhe.ai/",
            userAgent = "Aiyifan/1.0",
            cookie = "",
        )
        val applied = linkedMapOf<String, String>()

        headers.applyTo { key, value -> applied[key] = value }

        assertEquals(mapOf("Referer" to "https://baipiaozhe.ai/", "User-Agent" to "Aiyifan/1.0"), applied)
    }

    @Test
    fun `reports empty when all headers are blank`() {
        val headers = PlaybackHttpHeaders()

        assertTrue(headers.isEmpty())
        assertEquals(0, headers.entries.size)
    }

    @Test
    fun `entries expose cookie referer and user agent`() {
        val headers = PlaybackHttpHeaders(
            referer = "https://baipiaozhe.ai/",
            userAgent = "Aiyifan/1.0",
            cookie = "session=abc",
        )

        assertEquals(
            mapOf(
                "Referer" to "https://baipiaozhe.ai/",
                "User-Agent" to "Aiyifan/1.0",
                "Cookie" to "session=abc",
            ),
            headers.entries,
        )
    }

    @Test
    fun `holder clears session headers after direct playback ends`() {
        val holder = PlaybackHttpHeadersHolder()
        holder.update(PlaybackHttpHeaders(cookie = "session=abc"))

        holder.clear()

        assertTrue(holder.current().isEmpty())
    }
}

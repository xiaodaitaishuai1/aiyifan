package com.aiyifan.app.core.data.baipiaozhe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BaipiaozhePlaybackPayloadTest {

    @Test
    fun `parses bare m3u8 playback url`() {
        val episode = BaipiaozhePlaybackPayload.parse("https://cdn.example.com/video/index.m3u8")

        assertNotNull(episode)
        assertEquals("https://cdn.example.com/video/index.m3u8", episode!!.mediaUrl)
    }

    @Test
    fun `parses json payload with play_url and title`() {
        val episode = BaipiaozhePlaybackPayload.parse(
            """{"play_url":"https://cdn.example.com/movie.mp4","title":"Sample movie"}""",
        )

        assertNotNull(episode)
        assertEquals("https://cdn.example.com/movie.mp4", episode!!.mediaUrl)
        assertEquals("Sample movie", episode.episodeTitle)
    }

    @Test
    fun `parses json payload using url and src aliases with episode`() {
        val episode = BaipiaozhePlaybackPayload.parse(
            """{"url":"https://cdn.example.com/ep.mp4","src":"https://cdn.example.com/ep.mp4","episode":"03"}""",
        )

        assertNotNull(episode)
        assertEquals("https://cdn.example.com/ep.mp4", episode!!.mediaUrl)
        assertEquals("03", episode.episodeTitle)
    }

    @Test
    fun `parses MP4 url with query parameters`() {
        val episode = BaipiaozhePlaybackPayload.parse(
            "https://cdn.example.com/episode.mp4?token=temporary",
        )

        assertNotNull(episode)
        assertEquals("https://cdn.example.com/episode.mp4?token=temporary", episode!!.mediaUrl)
    }

    @Test
    fun `rejects blank payload`() {
        assertNull(BaipiaozhePlaybackPayload.parse(null))
        assertNull(BaipiaozhePlaybackPayload.parse("   "))
    }

    @Test
    fun `rejects non http media url`() {
        assertNull(BaipiaozhePlaybackPayload.parse("ftp://cdn.example.com/video.m3u8"))
        assertNull(BaipiaozhePlaybackPayload.parse("javascript:alert(1)"))
    }

    @Test
    fun `rejects HTTPS URL that is not direct media`() {
        assertNull(BaipiaozhePlaybackPayload.parse("https://baipiaozhe.ai/detail/42"))
    }

    @Test
    fun `rejects missing play url in json`() {
        assertNull(BaipiaozhePlaybackPayload.parse("""{"title":"No url here"}"""))
    }
}

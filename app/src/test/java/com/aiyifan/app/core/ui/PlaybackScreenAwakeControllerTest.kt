package com.aiyifan.app.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackScreenAwakeControllerTest {

    @Test
    fun `attach keeps screen awake when playback is active`() {
        val values = mutableListOf<Boolean>()
        val controller = PlaybackScreenAwakeController(values::add)

        controller.attach(isPlaying = true)

        assertEquals(listOf(true), values)
    }

    @Test
    fun `playback state changes update an attached screen`() {
        val values = mutableListOf<Boolean>()
        val controller = PlaybackScreenAwakeController(values::add)
        controller.attach(isPlaying = false)

        controller.onIsPlayingChanged(true)
        controller.onIsPlayingChanged(false)
        controller.onIsPlayingChanged(true)

        assertEquals(listOf(false, true, false, true), values)
    }

    @Test
    fun `detach clears screen awake and ignores later playback updates`() {
        val values = mutableListOf<Boolean>()
        val controller = PlaybackScreenAwakeController(values::add)
        controller.attach(isPlaying = true)

        controller.detach()
        controller.onIsPlayingChanged(true)

        assertEquals(listOf(true, false), values)
    }
}

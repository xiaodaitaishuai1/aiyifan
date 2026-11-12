package com.aiyifan.app.feature.mine

import org.junit.Assert.*
import org.junit.Test

class HeaderSlideshowTest {
    @Test fun `slow manual load commits only after readiness even when paused`() {
        val state = HeaderSlideshow(3, initialPaused = true)
        state.activate(0)
        state.loaded(0)
        state.beginInteraction()
        state.requestManual(-1)
        state.completeTransition(5000)
        assertEquals(0, state.index)
        assertFalse(state.tick(10000))
        state.loaded(10000)
        state.completeTransition(10450)
        state.endInteraction(10450)
        assertEquals(2, state.index)
        assertTrue(state.paused)
    }

    @Test fun `manual previous wraps while paused and skips failed pictures backwards`() {
        val state = HeaderSlideshow(4, initialPaused = true)
        state.activate(0)
        state.loaded(0)
        state.beginInteraction()
        assertTrue(state.requestManual(-1))
        assertEquals(3, state.pendingIndex)
        assertEquals(2, state.rejectNext(3, -1))
        state.loaded(100)
        state.completeTransition(550)
        state.endInteraction(550)
        assertEquals(2, state.index)
        assertTrue(state.paused)
        assertFalse(state.tick(10000))
    }

    @Test fun `touch cancels deadline and restarts a full interval on release`() {
        val state = HeaderSlideshow(3)
        state.activate(0)
        state.loaded(0)
        state.beginInteraction()
        assertFalse(state.tick(10000))
        state.endInteraction(10000)
        assertFalse(state.tick(14999))
        assertTrue(state.tick(15000))
    }

    @Test fun `cancelled drag keeps current index and allows the next interaction`() {
        val state = HeaderSlideshow(3)
        state.activate(0)
        state.loaded(0)
        state.beginInteraction()
        state.requestManual(-1)
        state.cancelPending()
        state.endInteraction(100)
        assertEquals(0, state.index)
        state.beginInteraction()
        assertTrue(state.requestManual(1))
        assertEquals(1, state.pendingIndex)
        state.deactivate()
        assertNull(state.pendingIndex)
        state.activate(1000)
        assertTrue(state.tick(6000))
    }

    @Test fun `prefetch failure skips next candidate without changing displayed image or timer`() {
        val state = HeaderSlideshow(3)
        state.activate(0)
        state.loaded(0)
        assertEquals(1, state.nextCandidate)
        assertEquals(2, state.rejectNext(1))
        assertEquals(0, state.index)
        assertFalse(state.tick(4999))
        assertTrue(state.tick(5000))
        assertEquals(2, state.pendingIndex)
    }

    @Test fun `advances only after five seconds and wraps`() {
        val state = HeaderSlideshow(2)
        state.activate(100)
        state.loaded(100)
        assertFalse(state.tick(5099))
        assertTrue(state.tick(5100))
        assertEquals(0, state.index)
        assertEquals(1, state.pendingIndex)
        state.loaded(5100)
        state.completeTransition(5550)
        assertTrue(state.tick(10550))
        assertEquals(1, state.index)
        state.loaded(10550)
        state.completeTransition(11000)
        assertEquals(0, state.index)
    }

    @Test fun `manual pause survives background and viewer return`() {
        val state = HeaderSlideshow(3)
        state.activate(0)
        state.loaded(0)
        state.togglePause(1)
        state.deactivate()
        state.activate(9000)
        assertFalse(state.tick(20000))
        state.togglePause(20000)
        assertFalse(state.tick(24999))
        assertTrue(state.tick(25000))
    }

    @Test fun `return starts a fresh interval and background never advances`() {
        val state = HeaderSlideshow(3)
        state.activate(0)
        state.loaded(0)
        state.deactivate()
        assertFalse(state.tick(10000))
        state.activate(10000)
        assertFalse(state.tick(14999))
        assertTrue(state.tick(15000))
    }

    @Test fun `restores index and pause and handles invalid saved index`() {
        val state = HeaderSlideshow(3, 2, true)
        assertEquals(2, state.index)
        assertTrue(state.paused)
        assertEquals(0, HeaderSlideshow(3, 99).index)
    }

    @Test fun `failed pictures are skipped and exhaustion stops retries`() {
        val state = HeaderSlideshow(2)
        assertTrue(state.failed())
        assertEquals(1, state.index)
        assertFalse(state.failed())
        assertTrue(state.exhausted)
        state.activate(0)
        assertFalse(state.tick(10000))
        assertFalse(HeaderSlideshow(0).failed())
    }

    @Test fun `successful load waits five seconds and excludes failed images`() {
        val state = HeaderSlideshow(3)
        state.activate(0)
        assertFalse(state.tick(10000))
        state.failed()
        state.loaded(10000)
        assertTrue(state.tick(15000))
        assertEquals(2, state.pendingIndex)
        state.loaded(15000)
        state.completeTransition(15450)
        assertTrue(state.tick(20450))
        assertEquals(1, state.pendingIndex)
    }
    @Test fun `animation completion starts the next full interval`() {
        val state = HeaderSlideshow(3)
        state.activate(0)
        state.loaded(0)
        assertTrue(state.tick(5000))
        assertEquals(0, state.index)
        state.loaded(5300)
        assertTrue(state.transitioning)
        assertFalse(state.tick(10000))
        state.completeTransition(5750)
        assertFalse(state.tick(10749))
        assertTrue(state.tick(10750))
    }

    @Test fun `pause during animation commits incoming image`() {
        val state = HeaderSlideshow(3)
        state.activate(0)
        state.loaded(0)
        state.tick(5000)
        state.loaded(5100)
        state.togglePause(5200)
        assertEquals(1, state.index)
        assertTrue(state.paused)
        assertFalse(state.transitioning)
        assertFalse(state.tick(20000))
    }

    @Test fun `background cancels pending transition without changing current image`() {
        val state = HeaderSlideshow(3)
        state.activate(0)
        state.loaded(0)
        state.tick(5000)
        state.loaded(5100)
        state.deactivate()
        assertEquals(0, state.index)
        assertNull(state.pendingIndex)
        assertFalse(state.transitioning)
    }

    @Test fun `failed next image keeps current visible and skips to another`() {
        val state = HeaderSlideshow(3)
        state.activate(0)
        state.loaded(0)
        state.tick(5000)
        assertTrue(state.failed())
        assertEquals(0, state.index)
        assertEquals(2, state.pendingIndex)
        assertFalse(state.failed())
        assertFalse(state.exhausted)
        assertNull(state.pendingIndex)
        assertFalse(state.tick(20000))
    }

    @Test fun `single image does not animate to itself`() {
        val state = HeaderSlideshow(1)
        state.activate(0)
        state.loaded(0)
        assertFalse(state.tick(5000))
    }

    @Test fun `viewer opens committed incoming image and returns with a fresh interval`() {
        val state = HeaderSlideshow(3)
        state.activate(0)
        state.loaded(0)
        state.tick(5000)
        state.loaded(5100)
        state.completeTransition(5200)
        state.deactivate()
        assertEquals(1, state.index)
        state.activate(12000)
        state.loaded(12100)
        assertFalse(state.tick(17099))
        assertTrue(state.tick(17100))
    }

    @Test fun `cancel loading before pause leaves displayed image unchanged`() {
        val state = HeaderSlideshow(3)
        state.activate(0)
        state.loaded(0)
        state.tick(5000)
        state.cancelPending()
        state.togglePause(5100)
        assertEquals(0, state.index)
        assertNull(state.pendingIndex)
        assertFalse(state.tick(20000))
    }
}


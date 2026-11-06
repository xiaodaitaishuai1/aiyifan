package com.aiyifan.app.core.data

import com.aiyifan.app.core.data.remote.RemoteCatalogRepository
import com.aiyifan.app.core.model.Episode
import com.aiyifan.app.core.model.VideoDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryMutationTest {
    @Test fun `delete selected records notifies observers and retains others`() {
        val repository = RemoteCatalogRepository()
        val episode = Episode("e", "第一集", 1, null)
        repository.saveHistory(VideoDetail("a", "A", "", 1), episode, 20, 100)
        repository.saveHistory(VideoDetail("b", "B", "", 1), episode, 20, 100)
        var changes = 0
        val unsubscribe = repository.addHistoryListener { changes++ }
        repository.removeHistory(setOf("a"))
        assertEquals(listOf("b"), repository.getHistory().map { it.mediaKey })
        assertEquals("第一集", repository.getHistory().single().episodeTitle)
        assertEquals(1, changes)
        repository.clearHistory()
        assertEquals(2, changes)
        unsubscribe()
        repository.saveHistory(VideoDetail("a", "A", "", 1), episode, 20, 100)
        assertEquals(2, changes)
    }

    @Test fun `old history restores without episode title and new title round trips`() {
        val old = """[{"mediaKey":"a","episodeKey":"e","title":"A","coverUrl":"","videoType":1,"progressMs":0,"durationMs":0,"updatedAt":0}]"""
        val history = WatchHistoryCodec.decode(old).single()
        assertNull(history.episodeTitle)
        val titled = history.copy(episodeTitle = "第一集")
        assertEquals(titled, WatchHistoryCodec.decode(WatchHistoryCodec.encode(listOf(titled))).single())
    }
}

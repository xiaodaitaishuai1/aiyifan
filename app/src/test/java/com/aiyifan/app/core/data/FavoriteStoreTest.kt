package com.aiyifan.app.core.data

import com.aiyifan.app.core.model.VideoDetail
import com.aiyifan.app.core.model.Episode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteStoreTest {
    private fun detail(vararg keys: String) = VideoDetail("m", "Title", "", 1,
        episodes = keys.mapIndexed { index, key -> Episode(key, key, index, null) })

    @Test fun `baseline reorder and new episodes use stable keys`() {
        val store = InMemoryFavoriteStore()
        store.toggle(detail("a", "b"), 10)
        val token = store.generation("m")!!
        assertFalse(store.tracking("m")!!.hasUpdates)
        store.update(detail("b", "a"), 20, token)
        assertFalse(store.tracking("m")!!.hasUpdates)
        store.update(detail("b", "a", "c"), 30, token)
        assertTrue(store.tracking("m")!!.hasUpdates)
        store.markKnown(detail("a", "b"))
        assertTrue(store.tracking("m")!!.hasUpdates)
        store.markKnown(detail("a", "b", "c"))
        assertFalse(store.tracking("m")!!.hasUpdates)
    }

    @Test fun `removed and readded favorite rejects stale check`() {
        val store = InMemoryFavoriteStore()
        store.toggle(detail("a"), 10)
        val token = store.generation("m")!!
        store.remove("m")
        store.toggle(detail("a"), 20)
        store.update(detail("a", "b"), 30, token)
        assertFalse(store.tracking("m")!!.hasUpdates)
        assertEquals(20L, store.tracking("m")!!.checkedAt)
    }
    @Test fun `persisted favorites restore tracking and removal clears snapshot`() {
        var payload: String? = null
        val store = InMemoryFavoriteStore(persist = { payload = it })
        store.toggle(detail("a"), 10)
        store.update(detail("a", "b"), 20, store.generation("m")!!)
        val restored = InMemoryFavoriteStore(payload)
        assertEquals(store.favorites(), restored.favorites())
        assertEquals(store.tracking("m"), restored.tracking("m"))
        assertTrue(restored.tracking("m")!!.hasUpdates)
        store.remove("m")
        assertTrue(InMemoryFavoriteStore(payload).favorites().isEmpty())
        assertNull(InMemoryFavoriteStore(payload).tracking("m"))
    }

    @Test fun `corrupt entries are skipped and legacy favorite gets first baseline`() {
        val payload = """[{}, {"mediaKey":"m","title":"Title","coverUrl":"","videoType":1,"createdAt":1}]"""
        val store = InMemoryFavoriteStore(payload)
        assertEquals(1, store.favorites().size)
        assertNull(store.tracking("m"))
        store.update(detail("a", "b"), 10, store.generation("m")!!)
        assertFalse(store.tracking("m")!!.hasUpdates)
        assertTrue(InMemoryFavoriteStore("broken json").favorites().isEmpty())
    }

}

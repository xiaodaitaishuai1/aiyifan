package com.aiyifan.app.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class ResourceAccessorTest {
    @Test
    fun `fake accessor formats resource arguments`() {
        val accessor = FakeResourceAccessor(mapOf(1 to "item %1\$d"))

        assertEquals("item 3", accessor.string(1, 3))
    }
}

private class FakeResourceAccessor(
    private val strings: Map<Int, String>,
) : ResourceAccessor {
    override fun string(id: Int, vararg formatArgs: Any): String =
        String.format(Locale.ROOT, checkNotNull(strings[id]), *formatArgs)

    override fun dimensionPixelSize(id: Int): Int = error("No dimension configured for $id")

    override fun color(id: Int): Int = error("No color configured for $id")
}

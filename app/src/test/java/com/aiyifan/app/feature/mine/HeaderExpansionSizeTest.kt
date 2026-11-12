package com.aiyifan.app.feature.mine

import org.junit.Assert.assertEquals
import org.junit.Test

class HeaderExpansionSizeTest {
    @Test fun `expanded height is four fifths including inset`() {
        assertEquals(1920, HeaderExpansionSize.heightPx(2400, 863))
    }
    @Test fun `short window never shrinks below collapsed height`() {
        assertEquals(840, HeaderExpansionSize.heightPx(1000, 840))
    }
    @Test fun `window changes recalculate fixed fraction`() {
        assertEquals(1600, HeaderExpansionSize.heightPx(2000, 700))
        assertEquals(2400, HeaderExpansionSize.heightPx(3000, 700))
    }
}

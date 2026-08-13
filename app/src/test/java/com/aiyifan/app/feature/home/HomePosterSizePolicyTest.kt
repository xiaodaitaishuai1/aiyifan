package com.aiyifan.app.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomePosterSizePolicyTest {

    @Test
    fun `selected indicator width is fourteen dp at xxhdpi`() {
        assertEquals(42, HomePosterSizePolicy.pxFromDp(14, 3f))
    }

    @Test
    fun `banner and card retain their own aspect ratios`() {
        assertEquals(HomeImageSize(1080, 540), HomePosterSizePolicy.banner(1080))
        assertEquals(HomeImageSize(528, 792), HomePosterSizePolicy.card(528))
    }
}

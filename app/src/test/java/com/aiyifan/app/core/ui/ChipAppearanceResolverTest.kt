package com.aiyifan.app.core.ui

import com.aiyifan.app.R
import org.junit.Assert.assertEquals
import org.junit.Test

class ChipAppearanceResolverTest {

    @Test
    fun `selected chip uses accent background and white text`() {
        assertEquals(
            ChipAppearance(
                backgroundRes = R.drawable.bg_chip_selected,
                textColorRes = R.color.white,
            ),
            ChipAppearanceResolver.forSelection(isSelected = true),
        )
    }

    @Test
    fun `unselected chip uses neutral background and primary text`() {
        assertEquals(
            ChipAppearance(
                backgroundRes = R.drawable.bg_chip,
                textColorRes = R.color.text_primary,
            ),
            ChipAppearanceResolver.forSelection(isSelected = false),
        )
    }
}

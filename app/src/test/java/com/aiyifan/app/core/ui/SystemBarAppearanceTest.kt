package com.aiyifan.app.core.ui

import android.content.res.Configuration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemBarAppearanceTest {

    @Test
    fun `night mode uses light system bar icons`() {
        assertFalse(usesLightSystemBarIcons(Configuration.UI_MODE_NIGHT_YES))
    }

    @Test
    fun `day mode uses dark system bar icons`() {
        assertTrue(usesLightSystemBarIcons(Configuration.UI_MODE_NIGHT_NO))
    }
}

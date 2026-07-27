package com.aiyifan.app.core.ui

import androidx.appcompat.app.AppCompatDelegate
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeModeTest {

    @Test
    fun `unknown stored value falls back to following system`() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorageValue("legacy"))
    }

    @Test
    fun `each mode maps to its AppCompat constant`() {
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, ThemeMode.SYSTEM.nightMode)
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, ThemeMode.LIGHT.nightMode)
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, ThemeMode.DARK.nightMode)
    }
}

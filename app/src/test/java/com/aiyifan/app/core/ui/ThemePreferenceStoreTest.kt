package com.aiyifan.app.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemePreferenceStoreTest {

    @Test
    fun `missing preference uses system mode`() {
        assertEquals(ThemeMode.SYSTEM, ThemePreferenceStore.resolve(null))
    }

    @Test
    fun `invalid preference uses system mode`() {
        assertEquals(ThemeMode.SYSTEM, ThemePreferenceStore.resolve("unsupported"))
    }
}

package com.aiyifan.app.core.ui

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

class ThemePreferenceStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun currentMode(): ThemeMode = resolve(preferences.getString(KEY_THEME_MODE, null))

    fun applyCurrentMode() {
        AppCompatDelegate.setDefaultNightMode(currentMode().nightMode)
    }

    fun select(mode: ThemeMode) {
        preferences.edit().putString(KEY_THEME_MODE, mode.storageValue).apply()
        AppCompatDelegate.setDefaultNightMode(mode.nightMode)
    }

    companion object {
        private const val PREFERENCES_NAME = "appearance"
        private const val KEY_THEME_MODE = "theme_mode"

        fun resolve(value: String?): ThemeMode = ThemeMode.fromStorageValue(value)
    }
}

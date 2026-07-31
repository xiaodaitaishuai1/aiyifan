package com.aiyifan.app.feature.video

import android.content.Context

class AutoSkipPreferenceStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun isEnabled(): Boolean = preferences.getBoolean(KEY_AUTO_SKIP, true)

    fun setEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_AUTO_SKIP, enabled).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "playback"
        const val KEY_AUTO_SKIP = "auto_skip_intro_outro"
    }
}

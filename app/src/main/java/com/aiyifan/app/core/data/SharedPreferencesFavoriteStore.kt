package com.aiyifan.app.core.data

import android.content.SharedPreferences

class SharedPreferencesFavoriteStore(preferences: SharedPreferences) : FavoriteStore by InMemoryFavoriteStore(
    payload = try { preferences.getString("favorites", null) } catch (_: ClassCastException) { null },
    persist = { preferences.edit().putString("favorites", it).apply() },
)

package com.aiyifan.app

import android.app.Application
import com.aiyifan.app.core.data.AppGraph
import com.aiyifan.app.core.ui.ThemePreferenceStore

class AiyifanApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemePreferenceStore(this).applyCurrentMode()
        AppGraph.initialize(this)
    }
}

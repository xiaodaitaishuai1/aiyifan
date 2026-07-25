package com.aiyifan.app.core.ui

import android.content.res.Configuration

fun usesLightSystemBarIcons(uiMode: Int): Boolean =
    uiMode and Configuration.UI_MODE_NIGHT_MASK != Configuration.UI_MODE_NIGHT_YES

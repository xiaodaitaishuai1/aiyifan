package com.aiyifan.app.core.ui

import android.app.Activity
import android.graphics.Color
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding

fun Activity.setupEdgeToEdge() {
    val lightSystemBars = usesLightSystemBarIcons(resources.configuration.uiMode)
    WindowCompat.setDecorFitsSystemWindows(window, false)
    window.statusBarColor = Color.TRANSPARENT
    window.navigationBarColor = Color.TRANSPARENT
    WindowCompat.getInsetsController(window, window.decorView).apply {
        isAppearanceLightStatusBars = lightSystemBars
        isAppearanceLightNavigationBars = lightSystemBars
    }
}

fun View.applySystemBarsPadding(
    left: Boolean = false,
    top: Boolean = false,
    right: Boolean = false,
    bottom: Boolean = false,
    growHeight: Boolean = false,
    shouldApply: () -> Boolean = { true },
) {
    val initialLeft = paddingLeft
    val initialTop = paddingTop
    val initialRight = paddingRight
    val initialBottom = paddingBottom
    val initialHeight = layoutParams?.height ?: 0

    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
        )
        val extraTop = if (top) bars.top else 0
        val extraBottom = if (bottom) bars.bottom else 0

        if (shouldApply()) {
            view.updatePadding(
                left = initialLeft + if (left) bars.left else 0,
                top = initialTop + extraTop,
                right = initialRight + if (right) bars.right else 0,
                bottom = initialBottom + extraBottom,
            )
        } else {
            view.setPadding(initialLeft, initialTop, initialRight, initialBottom)
        }
        if (growHeight && initialHeight > 0) {
            view.updateLayoutParams {
                height = initialHeight + extraTop + extraBottom
            }
        }
        insets
    }
    ViewCompat.requestApplyInsets(this)
}

package com.aiyifan.app.core.ui

import android.content.Context
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DimenRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat

interface ResourceAccessor {
    fun string(@StringRes id: Int, vararg formatArgs: Any): String

    fun dimensionPixelSize(@DimenRes id: Int): Int

    @ColorInt
    fun color(@ColorRes id: Int): Int
}

class AndroidResourceAccessor(
    private val context: Context,
) : ResourceAccessor {
    override fun string(id: Int, vararg formatArgs: Any): String =
        context.getString(id, *formatArgs)

    override fun dimensionPixelSize(id: Int): Int =
        context.resources.getDimensionPixelSize(id)

    override fun color(id: Int): Int = ContextCompat.getColor(context, id)
}

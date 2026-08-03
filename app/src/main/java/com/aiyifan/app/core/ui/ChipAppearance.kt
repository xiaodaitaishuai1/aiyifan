package com.aiyifan.app.core.ui

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import com.aiyifan.app.R

data class ChipAppearance(
    @param:DrawableRes val backgroundRes: Int,
    @param:ColorRes val textColorRes: Int,
)

object ChipAppearanceResolver {
    fun forSelection(isSelected: Boolean): ChipAppearance =
        if (isSelected) {
            ChipAppearance(R.drawable.bg_chip_selected, R.color.white)
        } else {
            ChipAppearance(R.drawable.bg_chip, R.color.text_primary)
        }
}

package com.aiyifan.app.feature.video

enum class FloatingTapAction {
    None,
    ToggleControls,
    RestoreActivity,
}

object FloatingTapPolicy {
    fun action(tapCount: Int, dragged: Boolean, scaled: Boolean): FloatingTapAction = when {
        dragged || scaled -> FloatingTapAction.None
        tapCount >= 2 -> FloatingTapAction.RestoreActivity
        else -> FloatingTapAction.ToggleControls
    }
}

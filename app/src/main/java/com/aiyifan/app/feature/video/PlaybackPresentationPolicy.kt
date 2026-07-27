package com.aiyifan.app.feature.video

import android.view.View
enum class PlaybackDestination {
    PICTURE_IN_PICTURE,
    OVERLAY,
    IN_APP_MINI_PLAYER,
    NONE,
}

data class PlaybackCapabilities(
    val hasPlayableMedia: Boolean,
    val isPrepared: Boolean,
    val isPlaying: Boolean,
    val supportsPictureInPicture: Boolean,
    val pictureInPictureEnabled: Boolean,
    val hasOverlayPermission: Boolean,
)

object PlaybackPresentationPolicy {
    fun destinationWhenLeaving(value: PlaybackCapabilities): PlaybackDestination = when {
        !value.hasPlayableMedia || !value.isPrepared || !value.isPlaying -> PlaybackDestination.NONE
        value.supportsPictureInPicture && value.pictureInPictureEnabled -> PlaybackDestination.PICTURE_IN_PICTURE
        value.hasOverlayPermission -> PlaybackDestination.OVERLAY
        else -> PlaybackDestination.IN_APP_MINI_PLAYER
    }
}

enum class VideoPlayerBackAction {
    EXIT_FULL_SCREEN,
    MINIMIZE_TO_IN_APP_PLAYER,
    NAVIGATE_UP,
}

object VideoPlayerBackBehavior {
    fun action(isFullScreen: Boolean, canMinimizeInApp: Boolean): VideoPlayerBackAction = when {
        isFullScreen -> VideoPlayerBackAction.EXIT_FULL_SCREEN
        canMinimizeInApp -> VideoPlayerBackAction.MINIMIZE_TO_IN_APP_PLAYER
        else -> VideoPlayerBackAction.NAVIGATE_UP
    }
}

object FullScreenControlVisibility {
    fun shouldShowExitButton(isFullScreen: Boolean, controllerVisibility: Int): Boolean =
        isFullScreen && controllerVisibility == View.VISIBLE
}

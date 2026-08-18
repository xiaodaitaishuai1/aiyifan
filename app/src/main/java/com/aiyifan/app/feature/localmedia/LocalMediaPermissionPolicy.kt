package com.aiyifan.app.feature.localmedia

object LocalMediaPermissionPolicy {
    fun permissionFor(sdkInt: Int): String {
        return if (sdkInt >= 33) {
            "android.permission.READ_MEDIA_VIDEO"
        } else {
            "android.permission.READ_EXTERNAL_STORAGE"
        }
    }
}

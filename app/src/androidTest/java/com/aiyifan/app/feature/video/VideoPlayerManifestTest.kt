package com.aiyifan.app.feature.video

import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VideoPlayerManifestTest {
    @Test
    fun appDeclaresVideoPresentationPermissions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS,
        )

        val declaredPermissions = packageInfo.requestedPermissions.orEmpty().toSet()
        assertTrue(declaredPermissions.containsAll(VIDEO_PRESENTATION_PERMISSIONS))
        assertFalse(declaredPermissions.contains("android.permission.WRITE_SETTINGS"))
    }

    @Test
    @SdkSuppress(minSdkVersion = 26)
    fun videoPlayerDeclaresPictureInPictureSupport() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = context.packageManager.getActivityInfo(
            ComponentName(context, VideoPlayerActivity::class.java),
            0,
        )

        assertTrue(info.flags and FLAG_SUPPORTS_PICTURE_IN_PICTURE != 0)
    }

    @Test
    @SdkSuppress(minSdkVersion = 29)
    fun floatingPlayerServiceDeclaresMediaPlaybackForegroundType() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = context.packageManager.getServiceInfo(
            ComponentName(context, FloatingPlayerService::class.java),
            0,
        )

        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK, info.foregroundServiceType)
    }

    private companion object {
        // This platform flag is hidden from the public Android SDK but is set by the manifest parser.
        const val FLAG_SUPPORTS_PICTURE_IN_PICTURE = 0x00400000

        val VIDEO_PRESENTATION_PERMISSIONS = setOf(
            "android.permission.FOREGROUND_SERVICE",
            "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK",
            "android.permission.SYSTEM_ALERT_WINDOW",
        )
    }
}

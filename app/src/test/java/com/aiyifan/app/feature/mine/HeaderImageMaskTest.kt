package com.aiyifan.app.feature.mine

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.aiyifan.app.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28, 35])
class HeaderImageMaskTest {
    @Test fun `single taps toggle mask double tap does not and recreation restores state`() {
        val controller = Robolectric.buildActivity(HeaderImageActivity::class.java,
            Intent().putExtra(HeaderImageActivity.EXTRA_IMAGE, "mine_header_001.webp"))
        controller.get().setTheme(R.style.AppTheme)
        controller.setup().visible()
        fun mask() = controller.get().window.decorView.findViewWithTag<View>("header_image_mask")
        fun image() = controller.get().window.decorView.findViewWithTag<ZoomImageView>("header_zoom_image")
        fun tap() {
            val now = SystemClock.uptimeMillis()
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                MotionEvent.obtain(now, now + if (action == MotionEvent.ACTION_UP) 20 else 0,
                    action, 100f, 100f, 0).also { image().dispatchTouchEvent(it); it.recycle() }
            }
        }
        try {
            assertNotNull(mask())
            assertEquals(View.VISIBLE, mask().visibility)
            assertFalse(mask().isClickable)
            tap()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
            assertEquals(View.GONE, mask().visibility)
            tap()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
            assertEquals(View.VISIBLE, mask().visibility)
            tap()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(80))
            tap()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
            assertEquals(View.VISIBLE, mask().visibility)
            image().performClick()
            assertEquals(View.GONE, mask().visibility)
            controller.recreate()
            assertEquals(View.GONE, mask().visibility)
        } finally { controller.pause().stop().destroy() }
        val fresh = Robolectric.buildActivity(HeaderImageActivity::class.java,
            Intent().putExtra(HeaderImageActivity.EXTRA_IMAGE, "mine_header_001.webp"))
        fresh.get().setTheme(R.style.AppTheme)
        fresh.setup()
        try { assertEquals(View.VISIBLE, fresh.get().window.decorView.findViewWithTag<View>("header_image_mask").visibility) }
        finally { fresh.pause().stop().destroy() }
    }
}

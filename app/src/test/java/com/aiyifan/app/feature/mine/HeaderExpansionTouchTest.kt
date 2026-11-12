package com.aiyifan.app.feature.mine

import android.app.Activity
import android.app.Application
import android.os.Looper
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.ScrollView
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.aiyifan.app.core.data.AppGraph
import com.aiyifan.app.feature.main.MainActivity
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
@Config(application = Application::class, sdk = [28, 35], qualifiers = "w411dp-h891dp-420dpi")
class HeaderExpansionTouchTest {
    @Test fun `historical size settings do not affect fixed pull height`() = assertFragmentPull(0)

    @Test fun `one pull scrolls page to top then expands without lifting finger`() = assertFragmentPull(120)

    @Test fun `scrolling distance does not count toward expansion threshold`() = assertFragmentPull(200, false)

    @Test fun `pull that does not reach page top keeps header collapsed`() = assertFragmentPull(200, false, 10)

    private fun assertFragmentPull(initialScroll: Int, expectExpanded: Boolean = true, stepDistance: Int = 20) {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.get()
        activity.setTheme(R.style.AppTheme)
        AppGraph.initialize(activity)
        controller.create(Bundle().apply { putInt(MainActivity.KEY_SELECTED_TAB, 2) }).start().resume().visible()
        val frame = activity.findViewById<FrameLayout>(R.id.fragmentContainer)
        val fragment = MineFragment()
        activity.supportFragmentManager.beginTransaction().add(frame.id, fragment).commitNow()
        val decor = activity.window.decorView
        val page = fragment.requireView() as ScrollView
        // Keep the scrolling regression meaningful on tall test windows after removing
        // the size-setting row; this represents additional content or larger fonts.
        if (initialScroll > 0) (page.getChildAt(0) as LinearLayout).addView(
            View(activity), LinearLayout.LayoutParams(1, 400))
        val header = page.findViewById<HeaderGestureLayout>(R.id.mineHeader)
        fun layoutPage() {
            shadowOf(Looper.getMainLooper()).idle()
        }
        try {
            layoutPage()
            for (legacySize in listOf("small", "medium", "large")) {
                activity.getSharedPreferences("appearance", 0).edit()
                    .putString("mine_header_expansion_size", legacySize).commit()
                controller.pause().resume()
                layoutPage()
                val size = HeaderExpansionSize
                header.restoreExpanded(false)
                page.scrollTo(0, initialScroll)
                layoutPage()
                assertEquals(initialScroll, page.scrollY)
                val start = SystemClock.uptimeMillis()
                for (step in 0..12) {
                    val action = when (step) {
                        0 -> MotionEvent.ACTION_DOWN
                        12 -> MotionEvent.ACTION_UP
                        else -> MotionEvent.ACTION_MOVE
                    }
                    MotionEvent.obtain(start, SystemClock.uptimeMillis(), action, 500f, 300f + step * stepDistance, 0).also {
                        page.dispatchTouchEvent(it)
                        it.recycle()
                    }
                    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(25))
                    layoutPage()
                    if (step in 5..11) {
                        assertEquals("Header must follow only the pull remaining after page scrolling",
                            header.collapsedHeightPx + (step * stepDistance - initialScroll).coerceAtLeast(0), header.height)
                    }
                }
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(350))
                layoutPage()
                assertEquals("$size scroll=${page.scrollY}", expectExpanded, header.expanded)
                assertEquals((initialScroll - 12 * stepDistance).coerceAtLeast(0), page.scrollY)
                assertEquals("$size window=${decor.height} target=${header.expandedHeightPx}",
                    if (expectExpanded) size.heightPx(decor.height, header.collapsedHeightPx) else header.collapsedHeightPx,
                    header.height)
            }
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test fun `pull uses fixed height on repeated expansion`() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        activity.setTheme(R.style.AppTheme)
        val page = LayoutInflater.from(activity).inflate(R.layout.fragment_mine, null) as ScrollView
        activity.setContentView(page)
        val header = page.findViewById<HeaderGestureLayout>(R.id.mineHeader)
        header.isPageAtTop = { page.scrollY == 0 }
        val windowHeight = 2400
        fun layoutPage() {
            page.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(windowHeight, View.MeasureSpec.EXACTLY))
            page.layout(0, 0, 1080, windowHeight)
        }
        fun pull() {
            val start = SystemClock.uptimeMillis()
            for (step in 0..12) {
                val action = when (step) {
                    0 -> MotionEvent.ACTION_DOWN
                    12 -> MotionEvent.ACTION_UP
                    else -> MotionEvent.ACTION_MOVE
                }
                MotionEvent.obtain(start, SystemClock.uptimeMillis(), action, 500f, 300f + step * 20f, 0).also {
                    page.dispatchTouchEvent(it)
                    it.recycle()
                }
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(25))
                layoutPage()
            }
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(350))
            layoutPage()
        }
        try {
            repeat(2) {
                val size = HeaderExpansionSize
                header.configureSize(windowHeight, 100)
                header.restoreExpanded(false)
                layoutPage()
                page.scrollTo(0, 0)
                pull()
                assertTrue("$size did not expand", header.expanded)
                assertEquals(size.heightPx(windowHeight, header.collapsedHeightPx), header.height)
            }
        } finally {
            controller.pause().stop().destroy()
        }
    }
}

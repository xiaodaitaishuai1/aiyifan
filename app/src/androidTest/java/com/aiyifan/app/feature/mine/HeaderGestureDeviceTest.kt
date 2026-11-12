package com.aiyifan.app.feature.mine

import android.os.SystemClock
import android.view.MotionEvent
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aiyifan.app.R
import com.aiyifan.app.feature.main.MainActivity
import com.google.android.material.tabs.TabLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HeaderGestureDeviceTest {
    @Test fun headerHandsVerticalScrollToPageAndDoesNotCaptureButtonTouches() {
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            var starts = 0
            val scroll = ScrollView(context)
            val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            val header = HeaderGestureLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(600, 700)
                listener = object : HeaderGestureLayout.Listener {
                    override fun onTouchStart() { starts++ }
                    override fun onHorizontalDrag(offset: Float) = Unit
                    override fun onTouchFinish(switchImage: Boolean) = Unit
                    override fun onExpandedChanged(expanded: Boolean) = Unit
                }
                isPageAtTop = { scroll.scrollY == 0 }
            }
            val button = ImageButton(context).apply { id = R.id.headerPlayback }
            header.addView(button, FrameLayout.LayoutParams(100, 100))
            content.addView(header)
            content.addView(android.view.View(context), LinearLayout.LayoutParams(600, 1600))
            scroll.addView(content)
            scroll.measure(android.view.View.MeasureSpec.makeMeasureSpec(600, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(900, android.view.View.MeasureSpec.EXACTLY))
            scroll.layout(0, 0, 600, 900)
            val now = SystemClock.uptimeMillis()
            fun send(action: Int, x: Float, y: Float, time: Long) {
                MotionEvent.obtain(now, now + time, action, x, y, 0).also {
                    scroll.dispatchTouchEvent(it)
                    it.recycle()
                }
            }
            send(MotionEvent.ACTION_DOWN, 40f, 40f, 0)
            send(MotionEvent.ACTION_UP, 40f, 40f, 20)
            assertEquals(0, starts)
            send(MotionEvent.ACTION_DOWN, 300f, 500f, 40)
            send(MotionEvent.ACTION_MOVE, 300f, 400f, 80)
            send(MotionEvent.ACTION_MOVE, 300f, 300f, 120)
            send(MotionEvent.ACTION_MOVE, 300f, 200f, 160)
            send(MotionEvent.ACTION_UP, 300f, 200f, 200)
            assertTrue("Default-height upward swipe must scroll page", scroll.scrollY > 0)
            assertFalse(header.expanded)
            assertEquals(1, starts)
        }
    }

    private fun state(activity: MainActivity): HeaderSlideshow {
        val fragment = activity.supportFragmentManager.findFragmentByTag(MineFragment::class.java.name)!!
        return MineFragment::class.java.getDeclaredField("slideshow").apply { isAccessible = true }
            .get(fragment) as HeaderSlideshow
    }

    private fun await(scenario: ActivityScenario<MainActivity>, condition: (MainActivity) -> Boolean) {
        val limit = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < limit) {
            var done = false
            scenario.onActivity { done = condition(it) }
            if (done) return
            SystemClock.sleep(50)
        }
        fail("Timed out waiting for header")
    }

    private fun swipe(scenario: ActivityScenario<MainActivity>, dxFraction: Float, dyDp: Float) {
        var down = 0L
        var x = 0f
        var y = 0f
        var dx = 0f
        var dy = 0f
        scenario.onActivity {
            val header = it.findViewById<HeaderGestureLayout>(R.id.mineHeader)
            x = header.width / 2f
            y = header.height / 3f
            dx = header.width * dxFraction
            dy = dyDp * it.resources.displayMetrics.density
            down = SystemClock.uptimeMillis()
            MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x, y, 0).also { event ->
                header.dispatchTouchEvent(event)
                event.recycle()
            }
        }
        repeat(12) { step ->
            SystemClock.sleep(25)
            scenario.onActivity {
                val header = it.findViewById<HeaderGestureLayout>(R.id.mineHeader)
                val fraction = (step + 1) / 12f
                MotionEvent.obtain(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE,
                    x + dx * fraction, y + dy * fraction, 0).also { event ->
                    header.dispatchTouchEvent(event)
                    event.recycle()
                }
            }
        }
        scenario.onActivity {
            MotionEvent.obtain(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x + dx, y + dy, 0).also { event ->
                it.findViewById<HeaderGestureLayout>(R.id.mineHeader).dispatchTouchEvent(event)
                event.recycle()
            }
        }
    }

    @Test fun bidirectionalSwipeWhilePausedAndExpandedStateSurviveRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.findViewById<TabLayout>(R.id.bottomTabs).getTabAt(2)!!.select() }
            await(scenario) { it.findViewById<ImageButton>(R.id.headerExpand).isEnabled }
            scenario.onActivity { it.findViewById<ImageButton>(R.id.headerPlayback).performClick() }
            swipe(scenario, -0.4f, 4f)
            await(scenario) { state(it).index == 1 }
            swipe(scenario, 0.4f, 0f)
            await(scenario) { state(it).index == 0 }
            swipe(scenario, 0.4f, 0f)
            await(scenario) { state(it).index == 187 }
            scenario.onActivity { assertTrue(state(it).paused) }
            swipe(scenario, 0.02f, 60f)
            await(scenario) {
                val header = it.findViewById<HeaderGestureLayout>(R.id.mineHeader)
                header.expanded && header.height == header.expandedHeightPx
            }
            scenario.recreate()
            await(scenario) { it.findViewById<ImageButton>(R.id.headerExpand).isEnabled }
            scenario.onActivity {
                val header = it.findViewById<HeaderGestureLayout>(R.id.mineHeader)
                assertTrue(header.expanded)
                assertEquals(header.expandedHeightPx, header.height)
                assertTrue(state(it).paused)
                assertEquals(187, state(it).index)
            }
            swipe(scenario, 0f, -60f)
            await(scenario) {
                val header = it.findViewById<HeaderGestureLayout>(R.id.mineHeader)
                !header.expanded && header.height == header.collapsedHeightPx
            }
        }
    }

    @Test fun repeatedCancelledGesturesDoNotChangeImageOrExpandedState() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.findViewById<TabLayout>(R.id.bottomTabs).getTabAt(2)!!.select() }
            await(scenario) { it.findViewById<ImageButton>(R.id.headerExpand).isEnabled }
            scenario.onActivity { it.findViewById<ImageButton>(R.id.headerPlayback).performClick() }
            repeat(8) {
                scenario.onActivity { activity ->
                    val header = activity.findViewById<HeaderGestureLayout>(R.id.mineHeader)
                    val now = SystemClock.uptimeMillis()
                    listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_CANCEL).forEach { action ->
                        MotionEvent.obtain(now, now + 20, action,
                            if (action == MotionEvent.ACTION_DOWN) 300f else 500f, 250f, 0).also {
                            header.dispatchTouchEvent(it)
                            it.recycle()
                        }
                    }
                }
            }
            SystemClock.sleep(700)
            scenario.onActivity {
                assertEquals(0, state(it).index)
                assertTrue(state(it).paused)
                assertFalse(it.findViewById<HeaderGestureLayout>(R.id.mineHeader).expanded)
            }
        }
    }
}

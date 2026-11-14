package com.aiyifan.app.feature.video

import android.app.Application
import android.view.View
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExpandableSynopsisViewTest {
    private fun createView() = ExpandableSynopsisView(RuntimeEnvironment.getApplication())

    private fun ExpandableSynopsisView.submit(text: String) {
        setSynopsis(text)
    }

    private fun View.measureAt(width: Int = 360) {
        measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        layout(0, 0, measuredWidth, measuredHeight)
    }

    private fun View.body(): TextView = findViewWithTag("synopsis_text")
    private fun View.toggle(): View = findViewWithTag("synopsis_toggle")

    @Test
    fun `long synopsis starts at two lines and expands without losing text`() {
        val view = createView()
        val fullText = "1994年，春江市税务局稽查员与刑警一起侦破发票案件。".repeat(20)
        view.submit(fullText)
        view.measureAt()
        assertEquals(2, view.body().lineCount)
        assertTrue(view.toggle().visibility == View.VISIBLE)
        assertTrue(view.body().layout.getEllipsisCount(1) > 0)
        view.toggle().performClick()
        view.measureAt()
        assertTrue(view.body().lineCount > 2)
        assertEquals(fullText, view.body().text.toString())
        view.performClick()
        view.measureAt()
        assertEquals(2, view.body().lineCount)
    }

    @Test
    fun `short and exactly two line text need no expansion affordance`() {
        val view = createView()
        for (text in listOf("简短介绍", "第一行\n第二行")) {
            view.submit(text)
            view.measureAt()
            assertEquals(View.GONE, view.toggle().visibility)
            assertFalse(view.isClickable)
            assertEquals(text, view.body().text.toString())
        }
    }

    @Test
    fun `empty synopsis hides and new content starts collapsed`() {
        val view = createView()
        view.submit("剧情简介".repeat(100))
        view.measureAt()
        view.performClick()
        view.submit("新的剧情".repeat(100))
        view.measureAt()
        assertEquals(2, view.body().lineCount)
        view.submit("  ")
        assertEquals(View.GONE, view.visibility)
        view.submit("新的简介")
        view.measureAt()
        assertEquals(View.VISIBLE, view.visibility)
        assertEquals(View.GONE, view.toggle().visibility)
    }

    @Test
    fun `overflow follows measured width rather than character count`() {
        val view = createView()
        view.submit("剧情简介里的几段文字和人物介绍。".repeat(2))
        view.measureAt(120)
        assertEquals(View.VISIBLE, view.toggle().visibility)
        view.measureAt(1600)
        assertEquals(View.GONE, view.toggle().visibility)
        assertFalse(view.isClickable)
    }
}

package com.aiyifan.app.feature.localmedia

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalMediaCardLayoutTest {

    @Test
    fun `local media card uses a single line title and landscape thumbnail`() {
        val layout = layoutFile().readText()

        assertTrue(layout.contains("android:maxLines=\"1\""))
        assertTrue(layout.contains("android:singleLine=\"true\""))
        assertTrue(layout.contains("android:layout_width=\"@dimen/dp_120\""))
        assertTrue(layout.contains("android:layout_height=\"@dimen/dp_72\""))
        assertTrue(layout.contains("android:layout_height=\"@dimen/dp_96\""))
    }

    private fun layoutFile(): File = sequenceOf(
        File("src/main/res/layout/item_local_video.xml"),
        File("app/src/main/res/layout/item_local_video.xml"),
    ).first(File::isFile)
}

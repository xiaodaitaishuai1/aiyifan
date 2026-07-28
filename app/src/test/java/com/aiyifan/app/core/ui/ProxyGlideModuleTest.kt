package com.aiyifan.app.core.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyGlideModuleTest {

    @Test
    fun `poster loader uses the direct image client`() {
        assertTrue(source().contains("clientFactory.createDirect()"))
    }

    private fun source(): String = sequenceOf(
        File("src/main/java/com/aiyifan/app/core/ui/ProxyGlideModule.kt"),
        File("app/src/main/java/com/aiyifan/app/core/ui/ProxyGlideModule.kt"),
    ).first(File::isFile).readText()
}

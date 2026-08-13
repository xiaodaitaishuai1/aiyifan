package com.aiyifan.app.feature.proxy

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxySettingsBackupContractTest {

    @Test
    fun `encrypted proxy settings are excluded from backup and device transfer`() {
        val manifest = file("app/src/main/AndroidManifest.xml").readText()
        val backupRules = file("app/src/main/res/xml/backup_rules.xml").readText()
        val extractionRules = file("app/src/main/res/xml/data_extraction_rules.xml").readText()

        assertTrue(manifest.contains("@xml/backup_rules"))
        assertTrue(manifest.contains("@xml/data_extraction_rules"))
        assertTrue(backupRules.contains("domain=\"sharedpref\""))
        assertTrue(backupRules.contains("path=\"encrypted_proxy_settings.xml\""))
        assertTrue(extractionRules.contains("<cloud-backup"))
        assertTrue(extractionRules.contains("<device-transfer"))
        assertTrue(extractionRules.contains("path=\"encrypted_proxy_settings.xml\""))
    }

    private fun file(path: String): File = sequenceOf(File(path), File(path.removePrefix("app/")))
        .first(File::isFile)
}

package com.aiyifan.app.feature.proxy

import java.security.GeneralSecurityException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EncryptedSettingsRecoveryPolicyTest {

    @Test
    fun `security failure clears unreadable encrypted preferences`() {
        assertTrue(EncryptedSettingsRecoveryPolicy.shouldClear(GeneralSecurityException()))
    }

    @Test
    fun `wrapped security failure clears unreadable encrypted preferences`() {
        assertTrue(EncryptedSettingsRecoveryPolicy.shouldClear(IllegalStateException(GeneralSecurityException())))
    }

    @Test
    fun `ordinary failure preserves preferences`() {
        assertFalse(EncryptedSettingsRecoveryPolicy.shouldClear(IllegalStateException()))
    }
}

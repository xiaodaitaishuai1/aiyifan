package com.aiyifan.app.feature.proxy

import java.security.GeneralSecurityException

internal object EncryptedSettingsRecoveryPolicy {
    fun shouldClear(error: Throwable): Boolean =
        generateSequence(error) { it.cause }
            .any { it is GeneralSecurityException }
}

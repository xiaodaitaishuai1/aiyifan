package com.aiyifan.app.feature.proxy.runtime

enum class SingBoxLogCategory {
    REALITY_HANDSHAKE,
    TLS_HANDSHAKE,
    TIMEOUT,
    REMOTE_REFUSED,
    OUTBOUND_FAILURE,
}

object SingBoxLogClassifier {
    fun classify(message: String): SingBoxLogCategory {
        val normalized = message.lowercase()
        return when {
            "reality" in normalized -> SingBoxLogCategory.REALITY_HANDSHAKE
            "tls" in normalized || "certificate" in normalized -> SingBoxLogCategory.TLS_HANDSHAKE
            "timeout" in normalized -> SingBoxLogCategory.TIMEOUT
            "connection refused" in normalized -> SingBoxLogCategory.REMOTE_REFUSED
            else -> SingBoxLogCategory.OUTBOUND_FAILURE
        }
    }
}

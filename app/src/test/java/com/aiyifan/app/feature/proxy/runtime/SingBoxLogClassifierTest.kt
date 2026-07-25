package com.aiyifan.app.feature.proxy.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class SingBoxLogClassifierTest {

    @Test
    fun `classifies outbound failures without retaining connection details`() {
        assertEquals(SingBoxLogCategory.REALITY_HANDSHAKE, SingBoxLogClassifier.classify("reality verification failed"))
        assertEquals(SingBoxLogCategory.TLS_HANDSHAKE, SingBoxLogClassifier.classify("tls handshake failure"))
        assertEquals(SingBoxLogCategory.TIMEOUT, SingBoxLogClassifier.classify("i/o timeout"))
        assertEquals(SingBoxLogCategory.REMOTE_REFUSED, SingBoxLogClassifier.classify("connection refused"))
        assertEquals(SingBoxLogCategory.OUTBOUND_FAILURE, SingBoxLogClassifier.classify("unexpected outbound error"))
    }
}

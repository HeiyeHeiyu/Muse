package io.zer0.ai.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ProviderErrorTest {
    @Test
    fun payloadTooLargeIsNonRetryableInvalidRequest() {
        val error = ProviderError.from(413, "request entity too large")

        assertEquals(ProviderError.InvalidRequest::class, error::class)
        assertFalse(error.isRetryable)
        assertEquals(413, error.httpCode)
    }
}

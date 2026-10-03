package io.zer0.ai.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderErrorTest {
    @Test
    fun payloadTooLargeIsNonRetryableInvalidRequest() {
        val error = ProviderError.from(413, "request entity too large")

        assertEquals(ProviderError.InvalidRequest::class, error::class)
        assertFalse(error.isRetryable)
        assertEquals(413, error.httpCode)
    }

    @Test
    fun retryAfterParserRejectsNegativeAndMalformedValues() {
        assertEquals(120, ProviderError.parseRetryAfter("120"))
        assertNull(ProviderError.parseRetryAfter("-1"))
        assertNull(ProviderError.parseRetryAfter("not-a-duration"))
        assertNull(ProviderError.parseRetryAfter(null))
    }

    @Test
    fun displayMessageRedactsProviderBodySecrets() {
        val error = ProviderError.from(
            400,
            """{"error":{"message":"invalid api key sk-live-super-secret","type":"invalid_request_error"}}""",
        )

        assertFalse(error.displayMessage.contains("sk-live-super-secret"))
        assertTrue(error.displayMessage.contains("HTTP 400"))
        assertTrue(error.displayMessage.contains("[REDACTED]"))
    }

    @Test
    fun throwableMessagesDoNotCopyCredentialQueryParameters() {
        val error = ProviderError.from(
            java.io.IOException("https://provider.example/v1?api_key=sk-live-query-secret"),
        )

        assertFalse(error.displayMessage.contains("sk-live-query-secret"))
        assertTrue(error.displayMessage.contains("[REDACTED]"))
    }
}

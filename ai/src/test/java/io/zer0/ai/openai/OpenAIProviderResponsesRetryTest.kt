package io.zer0.ai.openai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAIProviderResponsesRetryTest {

    @Test
    fun `responses retries retryable HTTP failures before any delta`() {
        assertTrue(shouldRetryResponsesFailure(408, false, 0, 3, false))
        assertTrue(shouldRetryResponsesFailure(429, false, 1, 3, false))
        assertTrue(shouldRetryResponsesFailure(503, false, 2, 3, false))
        assertTrue(shouldRetryResponsesFailure(null, false, 0, 3, false))
    }

    @Test
    fun `responses does not retry after output or abort or retry budget`() {
        assertFalse(shouldRetryResponsesFailure(503, true, 0, 3, false))
        assertFalse(shouldRetryResponsesFailure(503, false, 3, 3, false))
        assertFalse(shouldRetryResponsesFailure(503, false, 0, 3, true))
        assertFalse(shouldRetryResponsesFailure(400, false, 0, 3, false))
    }
}

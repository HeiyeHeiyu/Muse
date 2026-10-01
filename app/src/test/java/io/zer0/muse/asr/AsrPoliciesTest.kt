package io.zer0.muse.asr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AsrPoliciesTest {
    @Test
    fun `only rate limits and server errors are retryable`() {
        assertEquals(false, isRetryableAsrHttpStatus(400))
        assertEquals(false, isRetryableAsrHttpStatus(401))
        assertEquals(false, isRetryableAsrHttpStatus(403))
        assertEquals(false, isRetryableAsrHttpStatus(413))
        assertEquals(false, isRetryableAsrHttpStatus(422))
        assertEquals(true, isRetryableAsrHttpStatus(429))
        assertEquals(true, isRetryableAsrHttpStatus(500))
        assertEquals(true, isRetryableAsrHttpStatus(599))
        assertEquals(false, isRetryableAsrHttpStatus(600))
    }

    @Test
    fun `terminal ASR failure preserves transcript and clears amplitudes`() {
        val current = ASRState(
            status = ASRStatus.Listening,
            transcript = "recognized text",
            amplitudes = listOf(0.7f),
        )

        assertEquals(
            ASRStatus.Error,
            current.withAsrFailure("provider rejected request").status,
        )
        assertEquals("provider rejected request", current.withAsrFailure("provider rejected request").errorMessage)
        assertEquals("recognized text", current.withAsrFailure("provider rejected request").transcript)
        assertEquals(emptyList<Float>(), current.withAsrFailure("provider rejected request").amplitudes)
    }

    @Test
    fun `capture teardown failure is ignored after intentional stop or cancellation`() {
        assertEquals(false, shouldReportAsrCaptureFailure(ASRStatus.Stopping, true))
        assertEquals(false, shouldReportAsrCaptureFailure(ASRStatus.Listening, false))
        assertEquals(true, shouldReportAsrCaptureFailure(ASRStatus.Listening, true))
        assertEquals(false, shouldReportAsrCaptureFailure(ASRStatus.Error, true))
        assertEquals(false, shouldReportAsrCaptureFailure(ASRStatus.Idle, true))
    }

    @Test
    fun `stop finalization preserves errors but clears transient amplitudes`() {
        val failure = ASRState(
            status = ASRStatus.Error,
            isAvailable = true,
            transcript = "recognized text",
            errorMessage = "HTTP 401",
            amplitudes = listOf(0.2f, 0.8f),
        )

        assertEquals(
            failure.copy(amplitudes = emptyList()),
            failure.afterAsrStop(),
        )

        val stopping = ASRState(
            status = ASRStatus.Stopping,
            isAvailable = true,
            transcript = "recognized text",
            amplitudes = listOf(0.4f),
        )
        assertEquals(ASRStatus.Idle, stopping.afterAsrStop().status)
        assertEquals("recognized text", stopping.afterAsrStop().transcript)
        assertNull(stopping.afterAsrStop().errorMessage)
        assertEquals(emptyList<Float>(), stopping.afterAsrStop().amplitudes)
    }
}

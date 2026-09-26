package io.zer0.memory.observe

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

/** D3-P1: 失败分类器测试 — 钉住"能不能重试"的唯一判定。 */
class FailureClassifierTest {

    @Test
    fun `null error defaults to retryable`() {
        assertEquals(FailureKind.RETRYABLE, FailureClassifier.classify(null))
    }

    @Test
    fun `cancellation is fatal`() {
        assertEquals(FailureKind.FATAL, FailureClassifier.classify(CancellationException("cancelled")))
    }

    @Test
    fun `vm error is fatal`() {
        assertEquals(FailureKind.FATAL, FailureClassifier.classify(OutOfMemoryError("oom")))
    }

    @Test
    fun `socket timeout and io are retryable`() {
        assertEquals(FailureKind.RETRYABLE, FailureClassifier.classify(SocketTimeoutException("timeout")))
        assertEquals(FailureKind.RETRYABLE, FailureClassifier.classify(IOException("connection reset")))
    }

    @Test
    fun `http rate limit and 5xx are retryable`() {
        assertEquals(FailureKind.RETRYABLE, FailureClassifier.classify(RuntimeException("HTTP 429 Too Many Requests")))
        assertEquals(FailureKind.RETRYABLE, FailureClassifier.classify(RuntimeException("HTTP 503 Unavailable")))
    }

    @Test
    fun `http 4xx auth and validation are permanent`() {
        assertEquals(FailureKind.PERMANENT, FailureClassifier.classify(RuntimeException("HTTP 401 unauthorized")))
        assertEquals(FailureKind.PERMANENT, FailureClassifier.classify(RuntimeException("HTTP 400 bad request")))
    }

    @Test
    fun `parse failures are permanent`() {
        assertEquals(FailureKind.PERMANENT, FailureClassifier.classify(IllegalStateException("json 解析失败")))
        assertEquals(FailureKind.PERMANENT, FailureClassifier.classify(IllegalArgumentException("参数非法")))
    }

    @Test
    fun `unknown error defaults to retryable`() {
        assertEquals(FailureKind.RETRYABLE, FailureClassifier.classify(RuntimeException("something odd")))
    }
}

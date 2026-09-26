package io.zer0.memory.observe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** D3-P3: 失败退避器测试(指数增长 / 封顶 / 清零 / 独立)。 */
class MemoryStepBackoffTest {

    private var now = 1_000_000L
    private val backoff = MemoryStepBackoff(
        baseMs = 1000L,
        maxBackoffMs = 8000L,
        clock = { now },
    )

    @Test
    fun `allows run initially`() {
        assertTrue(backoff.shouldRun("compileFacts"))
        assertEquals(0, backoff.failureCount("compileFacts"))
    }

    @Test
    fun `failure blocks until window elapses`() {
        backoff.recordFailure("compileFacts")
        assertFalse("失败后应进入退避", backoff.shouldRun("compileFacts"))
        now += 1000L
        assertTrue("基础窗口过后应恢复", backoff.shouldRun("compileFacts"))
    }

    @Test
    fun `consecutive failures grow exponentially`() {
        backoff.recordFailure("x")
        assertEquals(1000L, backoff.remainingBackoffMs("x"))
        backoff.recordFailure("x")
        assertEquals(2000L, backoff.remainingBackoffMs("x"))
        backoff.recordFailure("x")
        assertEquals(4000L, backoff.remainingBackoffMs("x"))
        assertEquals(3, backoff.failureCount("x"))
    }

    @Test
    fun `backoff is capped`() {
        repeat(10) { backoff.recordFailure("x") }
        assertEquals("应封顶", 8000L, backoff.remainingBackoffMs("x"))
    }

    @Test
    fun `success clears backoff`() {
        backoff.recordFailure("x")
        assertFalse(backoff.shouldRun("x"))
        backoff.recordSuccess("x")
        assertTrue(backoff.shouldRun("x"))
        assertEquals(0, backoff.failureCount("x"))
    }

    @Test
    fun `steps are independent`() {
        backoff.recordFailure("a")
        assertTrue("b 不应受 a 影响", backoff.shouldRun("b"))
        assertFalse(backoff.shouldRun("a"))
    }
}

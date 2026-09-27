package io.zer0.muse.ui.chat

import io.zer0.ai.core.MessageRole
import io.zer0.ai.core.UIMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v2.x 导入预热:预算截断与简报的单测。
 */
class WarmupHistoryTest {
    private fun msg(i: Int) = UIMessage(role = MessageRole.USER, content = "m$i")

    @Test
    fun `预算内全量保留`() {
        val history = (1..5).map { msg(it) }
        val result = WarmupHistory.trimToBudget(history, budgetTokens = 1000) { 100 }
        assertFalse(result.truncated)
        assertEquals(5, result.history.size)
        assertEquals("m1", result.history.first().content)
    }

    @Test
    fun `超预算从最旧截断且保留最新`() {
        val history = (1..10).map { msg(it) }
        val result = WarmupHistory.trimToBudget(history, budgetTokens = 350) { 100 }
        assertTrue(result.truncated)
        assertEquals(3, result.history.size)
        assertEquals("m8", result.history.first().content)
        assertEquals("m10", result.history.last().content)
    }

    @Test
    fun `单条超预算至少保留最新一条`() {
        val history = (1..3).map { msg(it) }
        val result = WarmupHistory.trimToBudget(history, budgetTokens = 50) { 100 }
        assertTrue(result.truncated)
        assertEquals(1, result.history.size)
        assertEquals("m3", result.history.first().content)
    }

    @Test
    fun `空历史不截断`() {
        val result = WarmupHistory.trimToBudget(emptyList(), budgetTokens = 100) { 100 }
        assertFalse(result.truncated)
        assertTrue(result.history.isEmpty())
    }

    @Test
    fun `预算计算与兜底`() {
        assertEquals(4800, WarmupHistory.budgetTokensFor(8000))
        assertEquals(WarmupHistory.FALLBACK_BUDGET_TOKENS, WarmupHistory.budgetTokensFor(0))
        assertEquals(WarmupHistory.FALLBACK_BUDGET_TOKENS, WarmupHistory.budgetTokensFor(-1))
    }

    @Test
    fun `简报为系统消息且防认领`() {
        val briefing = WarmupHistory.briefingMessage(truncated = true)
        assertEquals(MessageRole.SYSTEM, briefing.role)
        assertTrue(briefing.content.contains("导入"))
        assertTrue(briefing.content.contains("最新"))
        assertTrue(briefing.content.contains("保留最近部分"))

        val full = WarmupHistory.briefingMessage(truncated = false)
        assertFalse(full.content.contains("保留最近部分"))
    }
}

package io.zer0.muse.transformer

import io.zer0.ai.core.MessageRole
import io.zer0.ai.core.UIMessage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [CompressionSummaryStore] 与 [reusableSummary] 单测。
 *
 * 覆盖 2026-09-29 审查发现:
 *  - C「摘要每轮重算且不落库」→ 进程内水位线让会话重载后的同一段历史免去第二次 LLM 调用
 *  - D「树重建还原已压缩消息」→ 水位线提供 coveredIds 供树重建过滤(见 ConversationTreeOrderingTest)
 */
class CompressionSummaryStoreTest {
    private fun msg(content: String): UIMessage = UIMessage(role = MessageRole.USER, content = content)

    @Before
    fun setUp() {
        CompressionSummaryStore.clearAllForTest()
    }

    @After
    fun tearDown() {
        CompressionSummaryStore.clearAllForTest()
    }

    @Test
    fun `记录后可按会话读回`() {
        CompressionSummaryStore.remember("s1", setOf("a", "b"), "摘要A")
        val entry = CompressionSummaryStore.entry("s1")
        assertNotNull(entry)
        assertEquals("摘要A", entry!!.summary)
        assertEquals(setOf("a", "b"), entry.coveredIds)
        assertNull(CompressionSummaryStore.entry("s2"))
    }

    @Test
    fun `会话 id 或摘要为空时不记录`() {
        CompressionSummaryStore.remember(null, setOf("a"), "摘要")
        CompressionSummaryStore.remember("", setOf("a"), "摘要")
        CompressionSummaryStore.remember("s1", setOf("a"), "   ")
        CompressionSummaryStore.remember("s1", emptySet(), "摘要")
        assertNull(CompressionSummaryStore.entry("s1"))
    }

    @Test
    fun `clear 只清掉指定会话`() {
        CompressionSummaryStore.remember("s1", setOf("a"), "摘要1")
        CompressionSummaryStore.remember("s2", setOf("b"), "摘要2")
        CompressionSummaryStore.clear("s1")
        assertNull(CompressionSummaryStore.entry("s1"))
        assertNotNull(CompressionSummaryStore.entry("s2"))
    }

    @Test
    fun `超出容量时淘汰最早写入的会话`() {
        repeat(33) { i -> CompressionSummaryStore.remember("s$i", setOf("id$i"), "摘要$i") }
        assertNull("最早的会话应被淘汰", CompressionSummaryStore.entry("s0"))
        assertNotNull("最近写入的会话应保留", CompressionSummaryStore.entry("s32"))
    }

    @Test
    fun `待压缩区间被完全覆盖时复用缓存摘要`() {
        val m1 = msg("m1")
        val m2 = msg("m2")
        val entry =
            CompressionSummaryStore.Entry(
                summary = "摘要A",
                coveredIds = setOf(m1.id.toString(), m2.id.toString(), "其他"),
            )
        assertEquals("摘要A", reusableSummary(entry, listOf(m1, m2)))
    }

    @Test
    fun `只覆盖一部分时不复用(否则会丢历史)`() {
        val m1 = msg("m1")
        val m2 = msg("m2")
        val entry = CompressionSummaryStore.Entry(summary = "摘要A", coveredIds = setOf(m1.id.toString()))
        assertNull(reusableSummary(entry, listOf(m1, m2)))
    }

    @Test
    fun `无缓存或待压缩区间为空时不复用`() {
        assertNull(reusableSummary(null, listOf(msg("m1"))))
        val entry = CompressionSummaryStore.Entry(summary = "摘要A", coveredIds = setOf("x"))
        assertNull(reusableSummary(entry, emptyList()))
    }

    @Test
    fun `复用判定用消息 id 比较而不是内容`() {
        val same = msg("同样内容")
        val entry = CompressionSummaryStore.Entry(summary = "摘要A", coveredIds = setOf(same.id.toString()))
        assertTrue(reusableSummary(entry, listOf(same)) != null)
        // 内容相同但换了 id(例如重新导入的同文消息)→ 不算覆盖,必须重新摘要
        assertNull(reusableSummary(entry, listOf(msg("同样内容"))))
    }
}

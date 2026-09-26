package io.zer0.muse.transformer

import io.zer0.ai.core.MessageRole
import io.zer0.ai.core.UIMessage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * B10: 流式 ThinkTag 实时剥离的轻量纯函数 [splitThinkTagsForStreaming] 单测。
 *
 * 覆盖: 闭合 / 未闭合 / 多块 / 无标签短路 / 已有 reasoning 跳过 / 大小写,
 * 以及 [ThinkTagTransformer.visualTransform] 对本函数的委托一致性。
 */
class StreamingThinkSplitTest {

    // ── 纯函数 splitThinkTagsForStreaming ─────────────────────────────

    @Test
    fun `no think marker returns original content unchanged`() {
        val (reasoning, content) = splitThinkTagsForStreaming("普通流式正文,无标签")
        assertNull(reasoning)
        assertEquals("普通流式正文,无标签", content)
    }

    @Test
    fun `closed think block split into reasoning and content`() {
        val (reasoning, content) = splitThinkTagsForStreaming("<think>我在想</think>答案是42")
        assertEquals("我在想", reasoning)
        assertEquals("答案是42", content)
    }

    @Test
    fun `closed block only leaves empty content`() {
        val (reasoning, content) = splitThinkTagsForStreaming("<think>只有思考</think>")
        assertEquals("只有思考", reasoning)
        assertEquals("", content)
    }

    @Test
    fun `unclosed think during streaming is surfaced as reasoning`() {
        // 流式中常见: 只到达 <think> 开头,还没等到 </think>
        val (reasoning, content) = splitThinkTagsForStreaming("<think>正在推理中")
        assertEquals("正在推理中", reasoning)
        assertEquals("", content)
    }

    @Test
    fun `closed block then unclosed remainder`() {
        val (reasoning, content) = splitThinkTagsForStreaming("<think>A</think>正文<think>B")
        assertEquals("A\nB", reasoning)
        assertEquals("正文", content)
    }

    @Test
    fun `multiple closed blocks joined by newline`() {
        val (reasoning, content) = splitThinkTagsForStreaming("<think>一</think><think>二</think>结论")
        assertEquals("一\n二", reasoning)
        assertEquals("结论", content)
    }

    @Test
    fun `empty think tag stripped while reasoning stays null`() {
        val (reasoning, content) = splitThinkTagsForStreaming("<think></think>正文")
        assertNull(reasoning)
        assertEquals("正文", content)
    }

    @Test
    fun `case insensitive think tag`() {
        val (reasoning, content) = splitThinkTagsForStreaming("<THINK>大写</THINK>正文")
        assertEquals("大写", reasoning)
        assertEquals("正文", content)
    }

    @Test
    fun `existing reasoning skips split`() {
        val (reasoning, content) = splitThinkTagsForStreaming("<think>新思考</think>正文", "已有推理")
        assertEquals("已有推理", reasoning)
        assertEquals("<think>新思考</think>正文", content)
    }

    @Test
    fun `thinking variant not triggered by streaming guard`() {
        // 与 visualTransform 守卫保持一致: 仅 <think> 触发本流式快速路径;
        // <thinking> 变体交给 updateAssistant 的完整清洗路径,这里保持原样。
        val (reasoning, content) = splitThinkTagsForStreaming("<thinking>推理</thinking>正文")
        assertNull(reasoning)
        assertEquals("<thinking>推理</thinking>正文", content)
    }

    // ── visualTransform 委托一致性 ────────────────────────────────────

    @Test
    fun `visualTransform delegates closed block`() = runTest {
        val msg = UIMessage(role = MessageRole.ASSISTANT, content = "<think>思考</think>回复")
        val result = ThinkTagTransformer().visualTransform(listOf(msg), TransformContext())[0]
        assertEquals("思考", result.reasoning)
        assertEquals("回复", result.content)
    }

    @Test
    fun `visualTransform surfaces unclosed think`() = runTest {
        val msg = UIMessage(role = MessageRole.ASSISTANT, content = "<think>实时思考")
        val result = ThinkTagTransformer().visualTransform(listOf(msg), TransformContext())[0]
        assertEquals("实时思考", result.reasoning)
        assertEquals("", result.content)
    }

    @Test
    fun `visualTransform leaves plain message untouched`() = runTest {
        val msg = UIMessage(role = MessageRole.ASSISTANT, content = "纯正文")
        val result = ThinkTagTransformer().visualTransform(listOf(msg), TransformContext())[0]
        assertEquals(msg, result)
    }

    @Test
    fun `visualTransform skips message with existing reasoning`() = runTest {
        val msg = UIMessage(role = MessageRole.ASSISTANT, content = "<think>x</think>y", reasoning = "已存在")
        val result = ThinkTagTransformer().visualTransform(listOf(msg), TransformContext())[0]
        assertEquals(msg, result)
    }
}

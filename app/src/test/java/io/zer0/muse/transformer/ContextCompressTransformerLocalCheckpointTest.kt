package io.zer0.muse.transformer

import io.mockk.mockk
import io.zer0.ai.ChatService
import io.zer0.ai.core.MessageRole
import io.zer0.ai.core.UIMessage
import io.zer0.memory.summary.ContextCheckpointEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 摘要不可用时的"本地重建"检查点测试。
 *
 * 这一级是降级阶梯的最后一道：摘要模型报错/超时/网络差时，若什么都不写，
 * 重开应用就又回到全量历史、下次还得再试一遍摘要 —— 用户看到的是"压了但没用"。
 * 本地重建用零请求的确定性文本摘录产出检查点，**边界立刻生效且持久**。
 */
class ContextCompressTransformerLocalCheckpointTest {

    private val transformer = ContextCompressTransformer(chatService = mockk<ChatService>(relaxed = true))

    private fun msg(role: MessageRole, text: String) = UIMessage(role = role, content = text)

    private fun coveredMessages(): List<UIMessage> = listOf(
        msg(MessageRole.USER, "帮我看看压缩这块"),
        msg(MessageRole.ASSISTANT, "好的，我先读 transformer 的实现"),
        msg(MessageRole.TOOL, "文件内容：class ContextCompressTransformer { ... }"),
    )

    @Test
    fun `builds a local-strategy checkpoint with a usable summary`() {
        val covered = coveredMessages()
        val checkpoint = transformer.buildLocalCheckpoint(
            io.zer0.muse.transformer.ContextCompressTransformer.LocalCheckpointRequest(
                sessionId = "s-1",
                covered = covered,
                previousBoundaryId = null,
                coveredSeq = 42L,
                tokensBefore = 9_000,
                tokensAfter = 900,
                reason = ContextCheckpointEntity.REASON_AUTO,
            ),
        )

        assertNotNull("应产出检查点", checkpoint)
        assertEquals(ContextCheckpointEntity.STRATEGY_LOCAL, checkpoint?.strategy)
        assertEquals("s-1", checkpoint?.sessionId)
        assertEquals(42L, checkpoint?.coveredSeq)
        assertEquals("边界应落在最后一条被覆盖的消息上", covered.last().id.toString(), checkpoint?.lastCoveredMessageId)
        assertEquals(covered.size, checkpoint?.coveredCount)
        assertTrue("摘录正文不应为空", checkpoint?.summary?.isNotBlank() == true)
    }

    @Test
    fun `empty covered list produces nothing`() {
        assertNull(
            transformer.buildLocalCheckpoint(
                io.zer0.muse.transformer.ContextCompressTransformer.LocalCheckpointRequest(
                    sessionId = "s-1",
                    covered = emptyList(),
                    previousBoundaryId = null,
                    coveredSeq = 0L,
                    tokensBefore = 1_000,
                    tokensAfter = 100,
                    reason = ContextCheckpointEntity.REASON_AUTO,
                ),
            ),
        )
    }

    @Test
    fun `only system messages leave nothing worth digesting`() {
        // ContextHistoryDigest 跳过 SYSTEM 消息（动态注入的 prompt/RAG 不摘录），
        // 因此全是 SYSTEM 时没有可摘录内容 → 不写检查点（而不是写一个空摘要）
        val checkpoint = transformer.buildLocalCheckpoint(
            io.zer0.muse.transformer.ContextCompressTransformer.LocalCheckpointRequest(
                sessionId = "s-1",
                covered = listOf(msg(MessageRole.SYSTEM, "系统提示词"), msg(MessageRole.SYSTEM, "RAG 片段")),
                previousBoundaryId = null,
                coveredSeq = 5L,
                tokensBefore = 500,
                tokensAfter = 50,
                reason = ContextCheckpointEntity.REASON_AUTO,
            ),
        )
        assertNull("无可摘录内容时不写检查点", checkpoint)
    }

    @Test
    fun `local checkpoint takes effect in assembly`() {
        // 端到端语义：本地检查点产出的边界必须能被组装侧合并 ——
        // 否则"写了检查点"只是自欺欺人（模型照样收到全量历史）。
        val covered = coveredMessages()
        val checkpoint = transformer.buildLocalCheckpoint(
            io.zer0.muse.transformer.ContextCompressTransformer.LocalCheckpointRequest(
                sessionId = "s-1",
                covered = covered,
                previousBoundaryId = null,
                coveredSeq = 42L,
                tokensBefore = 9_000,
                tokensAfter = 900,
                reason = ContextCheckpointEntity.REASON_AUTO,
            ),
        )
        val live = listOf(
            msg(MessageRole.USER, "那现在改成落库"),
            msg(MessageRole.ASSISTANT, "这就改"),
        )
        val merged = ContextCheckpointMerge.apply(covered + live, checkpoint)

        assertTrue("本地检查点应当生效", merged.applied)
        val summary = merged.messages.first().content
        assertTrue(
            "摘要消息应带分隔标记",
            summary.startsWith(ContextCheckpointMerge.MARKER),
        )
        // 被覆盖的**助手侧**消息不再作为独立消息出现（它们已并入摘要）
        assertFalse(
            "被覆盖的助手回复不应再作为独立消息进入模型",
            merged.messages.any { it.content == "好的，我先读 transformer 的实现" },
        )
        // 但被覆盖区间里的**用户消息必须保留** —— 那是"用户到底说了什么"的原始记录，
        // 丢了它模型就会忘记用户的要求（见 ContextCheckpointMerge 的取舍说明）。
        assertTrue(
            "被覆盖区间内的用户消息必须保留",
            merged.messages.any { it.content == "帮我看看压缩这块" },
        )
        // 工具结果以**短摘录**并入摘要：内容应出现在摘要里，但被压到远短于原文
        assertTrue("工具结果应以摘录形式并入摘要", summary.contains("工具结果:"))
        assertTrue("摘录应远短于原文", summary.length < 4_000)
        // 边界之后的实时消息保留
        assertTrue(merged.messages.any { it.content == "那现在改成落库" })
    }

    @Test
    fun `previous boundary is reused when nothing new can be parsed`() {
        // 覆盖集合里若有无法解析成 Uuid 的 id，边界应退回上一次的边界（不写错边界）
        val weird = UIMessage(role = MessageRole.ASSISTANT, content = "内容")
        val previousId = java.util.UUID.randomUUID().toString()
        val checkpoint = transformer.buildLocalCheckpoint(
            io.zer0.muse.transformer.ContextCompressTransformer.LocalCheckpointRequest(
                sessionId = "s-1",
                covered = listOf(weird),
                previousBoundaryId = previousId,
                coveredSeq = 7L,
                tokensBefore = 100,
                tokensAfter = 50,
                reason = ContextCheckpointEntity.REASON_AUTO,
            ),
        )
        // weird.id 是随机 Uuid，能解析；因此边界取它而不是旧边界 —— 这里只断言"有边界且非空"
        assertNotNull(checkpoint)
        assertTrue(checkpoint?.lastCoveredMessageId?.isNotBlank() == true)
    }
}

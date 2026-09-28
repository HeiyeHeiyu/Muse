package io.zer0.muse.data.chat

import io.zer0.ai.core.MessageRole
import io.zer0.ai.core.UIMessage
import kotlin.uuid.Uuid
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationTreeOrderingTest {
    @Test
    fun `snapshot placeholder is never merged into real messages`() {
        val realUser = UIMessage(
            id = Uuid.random(),
            role = MessageRole.USER,
            content = "真实问题",
            createdAt = 100L,
            seq = 1L,
        )
        val realAssistant = UIMessage(
            id = Uuid.random(),
            role = MessageRole.ASSISTANT,
            content = "真实回答",
            createdAt = 101L,
            seq = 2L,
            parentGroupId = realUser.id.toString(),
            variantGroupId = "assistant-group",
        )
        val snapshotPlaceholder = UIMessage(
            id = Uuid.random(),
            role = MessageRole.USER,
            content = "",
            createdAt = 0L,
            variantGroupId = "old-group",
        )

        val merged = mergeRebuildMessages(
            ConversationTree.build(listOf(snapshotPlaceholder)),
            listOf(realAssistant, realUser),
        )

        assertEquals(listOf(realUser.id, realAssistant.id), merged.map { it.id })
    }

    @Test
    fun `message ordering uses stable sequence before createdAt`() {
        val first = UIMessage(
            id = Uuid.random(), role = MessageRole.USER, content = "第一轮",
            createdAt = 9_999L, seq = 1L,
        )
        val second = UIMessage(
            id = Uuid.random(), role = MessageRole.ASSISTANT, content = "第二条",
            createdAt = 1L, seq = 2L,
        )

        assertEquals(
            listOf(first.id, second.id),
            orderConversationMessages(listOf(second, first)).map { it.id },
        )
    }

    @Test
    fun `混合 seq 与 createdAt 时排序仍是全序(回归-比较器成环)`() {
        // 审查发现的成环三元组:旧实现会同时得出 A>C(按 seq)、A<B、B<C(按 createdAt)
        val a =
            UIMessage(
                id = Uuid.random(),
                role = MessageRole.USER,
                content = "A",
                createdAt = 3_000L,
                seq = 10L,
            )
        val b =
            UIMessage(
                id = Uuid.random(),
                role = MessageRole.USER,
                content = "B(无序旧消息)",
                createdAt = 1_000L,
                seq = 0L,
            )
        val c =
            UIMessage(
                id = Uuid.random(),
                role = MessageRole.ASSISTANT,
                content = "C",
                createdAt = 100L,
                seq = 20L,
            )

        // 不应抛 "Comparison method violates its general contract!"
        val first = orderConversationMessages(listOf(a, b, c)).map { it.id }
        // 有序消息仍按 seq(A 在 C 前),无序消息按 createdAt 插回时间段(B 的时间早于 A、晚于 C)
        assertEquals(listOf(a.id, b.id, c.id), first)
        // 结果与输入顺序无关(全序的判据)
        assertEquals(first, orderConversationMessages(listOf(c, b, a)).map { it.id })
        assertEquals(first, orderConversationMessages(listOf(b, c, a)).map { it.id })
    }

    @Test
    fun `无序旧消息按 createdAt 插回时间段而不是统一塞到一端`() {
        val m1 =
            UIMessage(
                id = Uuid.random(),
                role = MessageRole.USER,
                content = "m1",
                createdAt = 10L,
                seq = 1L,
            )
        val m2 =
            UIMessage(
                id = Uuid.random(),
                role = MessageRole.ASSISTANT,
                content = "m2",
                createdAt = 20L,
                seq = 2L,
            )
        val legacy =
            UIMessage(
                id = Uuid.random(),
                role = MessageRole.USER,
                content = "legacy",
                createdAt = 15L,
                seq = 0L,
            )

        assertEquals(
            listOf(m1.id, legacy.id, m2.id),
            orderConversationMessages(listOf(m2, legacy, m1)).map { it.id },
        )
    }
}

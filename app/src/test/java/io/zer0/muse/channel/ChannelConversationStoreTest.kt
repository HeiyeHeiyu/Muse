package io.zer0.muse.channel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ChannelConversationStore.applyCompression] 单测。
 *
 * 重点覆盖 2026-09-29 审查发现的"渠道滚动摘要丢轮次":
 * 原实现是"读快照 → 调 LLM → 整份覆盖回写",压缩期间新 append 的轮次会被丢掉。
 */
class ChannelConversationStoreTest {
    private fun seed(
        channelId: String,
        from: String,
        count: Int,
        prefix: String = "turn",
    ): List<ChannelConversationStore.Turn> {
        ChannelConversationStore.clear(channelId, from)
        repeat(count) { i ->
            ChannelConversationStore.append(channelId, from, if (i % 2 == 0) "user" else "assistant", "$prefix-$i")
        }
        return ChannelConversationStore.conversation(channelId, from)?.turns.orEmpty()
    }

    @Test
    fun `压缩回写保留压缩期间新到的轮次`() {
        val channelId = "ch-regression"
        val from = "peer-regression"
        val turns = seed(channelId, from, 6)
        val snapshot = ChannelConversationStore.conversation(channelId, from)!!
        val drained = turns.take(4)

        // 压缩进行中(LLM 调用期间)用户又发了一条
        ChannelConversationStore.append(channelId, from, "user", "压缩期间的新消息")

        val applied =
            ChannelConversationStore.applyCompression(
                channelId = channelId,
                from = from,
                snapshotSummary = snapshot.summary,
                drainedTurns = drained,
                summary = "摘要A",
            )

        assertTrue("应写入成功", applied)
        val now = ChannelConversationStore.conversation(channelId, from)!!
        assertEquals("摘要A", now.summary)
        // 已压缩的 4 轮被摘掉;保留的最近 2 轮 + 压缩期间新到的那条都必须在
        assertEquals(listOf("turn-4", "turn-5", "压缩期间的新消息"), now.turns.map { it.text })
    }

    @Test
    fun `摘要已被另一次压缩更新时放弃写入`() {
        val channelId = "ch-cas"
        val from = "peer-cas"
        val turns = seed(channelId, from, 6)
        val snapshot = ChannelConversationStore.conversation(channelId, from)!!

        // 第一次压缩成功
        assertTrue(
            ChannelConversationStore.applyCompression(channelId, from, snapshot.summary, turns.take(4), "摘要A"),
        )
        val afterFirst = ChannelConversationStore.conversation(channelId, from)!!

        // 第二次压缩用的是"压缩前"的快照(摘要为空)→ 必须放弃,不能把摘要覆盖回旧内容,
        // 也不能再摘掉 4 轮(否则会误删上一轮之后的新消息)
        val applied =
            ChannelConversationStore.applyCompression(channelId, from, snapshot.summary, turns.take(4), "摘要B")

        assertFalse("摘要已被更新,应放弃写入", applied)
        val afterSecond = ChannelConversationStore.conversation(channelId, from)!!
        assertEquals("摘要A", afterSecond.summary)
        assertEquals(afterFirst.turns.map { it.text }, afterSecond.turns.map { it.text })
    }

    @Test
    fun `边界轮次对不上时放弃写入`() {
        val channelId = "ch-boundary"
        val from = "peer-boundary"
        val turns = seed(channelId, from, 6)
        val snapshot = ChannelConversationStore.conversation(channelId, from)!!

        // 对话在压缩期间被清空重建(边界轮次不再是同一条:文本不同)
        seed(channelId, from, 6, prefix = "rebuilt")

        val applied =
            ChannelConversationStore.applyCompression(channelId, from, snapshot.summary, turns.take(4), "摘要A")

        assertFalse("边界对不上应放弃", applied)
        assertEquals(6, ChannelConversationStore.conversation(channelId, from)!!.turns.size)
    }

    @Test
    fun `正常压缩摘掉已并入摘要的轮次并写入摘要`() {
        val channelId = "ch-happy"
        val from = "peer-happy"
        val turns = seed(channelId, from, 5)

        val applied =
            ChannelConversationStore.applyCompression(
                channelId = channelId,
                from = from,
                snapshotSummary = "",
                drainedTurns = turns.take(3),
                summary = "摘要X",
            )

        assertTrue(applied)
        val now = ChannelConversationStore.conversation(channelId, from)!!
        assertEquals("摘要X", now.summary)
        assertEquals(listOf("turn-3", "turn-4"), now.turns.map { it.text })
        assertTrue("updatedAt 应被刷新", now.updatedAt > 0L)
    }

    @Test
    fun `无轮次可摘时放弃写入`() {
        val channelId = "ch-empty"
        val from = "peer-empty"
        seed(channelId, from, 3)

        val applied =
            ChannelConversationStore.applyCompression(channelId, from, "", emptyList(), "摘要Y")

        assertFalse(applied)
        assertEquals("", ChannelConversationStore.conversation(channelId, from)!!.summary)
    }
}

package io.zer0.muse.channel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChannelAutoReplyConfigTest {

    @Test
    fun `webhook reply uses the channel whose credentials authenticated the event`() {
        val first = ChannelConfig("feishu-a", ChannelPlatform.FEISHU, autoReply = true)
        val second = ChannelConfig("feishu-b", ChannelPlatform.FEISHU, autoReply = true)
        val inbound = ChannelInbox.Inbound(
            platform = ChannelPlatform.FEISHU.name,
            sourceChannelId = second.id,
        )

        assertEquals(second, selectAutoReplyConfig(listOf(first, second), inbound))
    }

    @Test
    fun `missing authenticated channel id does not fall through to another channel`() {
        val channel = ChannelConfig("qq-a", ChannelPlatform.QQ, autoReply = true)
        val inbound = ChannelInbox.Inbound(
            platform = ChannelPlatform.QQ.name,
            sourceChannelId = "qq-removed",
        )

        assertNull(selectAutoReplyConfig(listOf(channel), inbound))
    }

    @Test
    fun `legacy inbound without a source channel does not pick an arbitrary account`() {
        val channel = ChannelConfig("qq-a", ChannelPlatform.QQ, autoReply = true)
        val inbound = ChannelInbox.Inbound(platform = ChannelPlatform.QQ.name)

        assertNull(selectAutoReplyConfig(listOf(channel), inbound))
    }

    @Test
    fun `same sender and text on different accounts are not collapsed together`() {
        val first = channelAutoReplyDedupKey("QQ", "qq-a", "user", "hello")
        val second = channelAutoReplyDedupKey("QQ", "qq-b", "user", "hello")

        assertNotEquals(first, second)
    }
}

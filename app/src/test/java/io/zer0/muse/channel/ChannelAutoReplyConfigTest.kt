package io.zer0.muse.channel

import org.junit.Assert.assertEquals
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
    fun `qq passive reply binding expires with the platform reply window`() {
        val now = 1_800_000_000_000L

        assertEquals("message-1", qqReplyEventIdOverride("QQ", "message-1", now - 1_000L, now))
        assertEquals("", qqReplyEventIdOverride("QQ", "expired", now - QqMsgIdCache.VALID_WINDOW_MS - 1L, now))
        assertEquals("", qqReplyEventIdOverride("QQ", "", now, now))
        assertNull(qqReplyEventIdOverride("FEISHU", "event-1", now, now))
    }
}

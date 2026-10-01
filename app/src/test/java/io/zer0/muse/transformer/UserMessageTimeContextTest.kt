package io.zer0.muse.transformer

import io.zer0.ai.core.MessageRole
import io.zer0.ai.core.UIMessage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class UserMessageTimeContextTest {
    @Test
    fun `prompt uses the two user message timestamps instead of tool round duration`() {
        val messages = listOf(
            userMessage("previous", "2026-10-01T09:00:00Z"),
            UIMessage(
                role = MessageRole.ASSISTANT,
                content = "I will check.",
                createdAt = Instant.parse("2026-10-01T09:01:00Z").toEpochMilli(),
            ),
            UIMessage(
                role = MessageRole.TOOL,
                content = "current time: 09:01",
                createdAt = Instant.parse("2026-10-01T09:01:03Z").toEpochMilli(),
            ),
            userMessage("current", "2026-10-01T09:01:05Z"),
        )

        val context = UserMessageTimeContext.build(messages, ZoneId.of("UTC"))

        assertTrue(context.contains("2026-10-01 09:00:00 UTC"))
        assertTrue(context.contains("2026-10-01 09:01:05 UTC"))
        assertTrue(context.contains("1 分 5 秒"))
        assertTrue(context.contains("不能用助手思考、工具执行或 API 请求耗时替代"))
    }

    @Test
    fun `one user message does not invent a previous message interval`() {
        val context = UserMessageTimeContext.build(
            listOf(userMessage("only", "2026-10-01T09:00:00Z")),
            ZoneId.of("UTC"),
        )

        assertTrue(context.contains("没有可比较的上一条用户消息"))
        assertFalse(context.contains("消息间隔:"))
    }

    private fun userMessage(id: String, timestamp: String) = UIMessage(
        role = MessageRole.USER,
        content = id,
        createdAt = Instant.parse(timestamp).toEpochMilli(),
    )
}

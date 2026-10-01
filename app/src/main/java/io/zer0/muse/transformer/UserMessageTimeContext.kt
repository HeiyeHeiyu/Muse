package io.zer0.muse.transformer

import io.zer0.ai.core.MessageRole
import io.zer0.ai.core.UIMessage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Provides stable message-time anchors so tool latency is not mistaken for user-message elapsed time. */
internal object UserMessageTimeContext {
    private val timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z", Locale.ROOT)

    fun build(messages: List<UIMessage>, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val userMessages = messages.filter { it.role == MessageRole.USER && it.createdAt > 0L }
        val current = userMessages.lastOrNull() ?: return ""
        val currentTimestamp = formatTimestamp(current.createdAt, zoneId)
        val previous = userMessages.getOrNull(userMessages.lastIndex - 1)
        return if (previous == null) {
            "用户消息时间锚点（设备时区 ${zoneId.id}）：当前用户消息发送于 $currentTimestamp；" +
                "没有可比较的上一条用户消息，不要根据助手思考、工具执行或 API 调用耗时推算消息间隔。"
        } else {
            val previousTimestamp = formatTimestamp(previous.createdAt, zoneId)
            val elapsedMs = if (current.createdAt >= previous.createdAt) {
                current.createdAt - previous.createdAt
            } else {
                previous.createdAt - current.createdAt
            }
            val elapsed = formatElapsed(elapsedMs)
            "用户消息时间锚点（设备时区 ${zoneId.id}）：上一条用户消息发送于 $previousTimestamp；" +
                "当前用户消息发送于 $currentTimestamp；两条用户消息间隔：$elapsed。" +
                "回答消息间隔问题时必须使用该间隔，不能用助手思考、工具执行或 API 请求耗时替代。"
        }
    }

    private fun formatTimestamp(timestamp: Long, zoneId: ZoneId): String =
        Instant.ofEpochMilli(timestamp).atZone(zoneId).format(timestampFormatter)

    private fun formatElapsed(elapsedMs: Long): String {
        if (elapsedMs < 1_000L) return "$elapsedMs 毫秒"
        val totalSeconds = elapsedMs / 1_000L
        val days = totalSeconds / 86_400L
        val hours = totalSeconds % 86_400L / 3_600L
        val minutes = totalSeconds % 3_600L / 60L
        val seconds = totalSeconds % 60L
        return buildList {
            if (days > 0) add("$days 天")
            if (hours > 0) add("$hours 小时")
            if (minutes > 0) add("$minutes 分")
            if (seconds > 0) add("$seconds 秒")
        }.joinToString(" ").ifBlank { "0 秒" }
    }
}

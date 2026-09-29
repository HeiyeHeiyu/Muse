package io.zer0.muse.ui.chat

import io.zer0.ai.core.ContextHistoryDigest
import io.zer0.ai.core.UIMessage
import io.zer0.ai.core.splitContextWindow

/**
 * 组装"上下文窗口 + 窗口外历史摘录"。
 *
 * 窗口外的消息不再被静默丢弃:先对它们做确定性摘录([ContextHistoryDigest],
 * 保留用户/助手自然语言片段与工具调用摘要),摘录消息插在窗口之前。
 * 这样即使工具密集会话把窗口占满,模型依然能看到更早对话的要点,
 * 不会出现"上下文断了、像失忆一样"的表现。
 *
 * 供 resolveHistory(每轮生成)与 pre-send 预警截断(近 token 上限时)共用。
 */
internal fun buildContextWindow(history: List<UIMessage>, maxSize: Int): List<UIMessage> {
    val (dropped, kept) = history.splitContextWindow(maxSize)
    if (dropped.isEmpty()) return kept
    val digest = ContextHistoryDigest.build(dropped)
    return if (digest == null) kept else listOf(digest) + kept
}

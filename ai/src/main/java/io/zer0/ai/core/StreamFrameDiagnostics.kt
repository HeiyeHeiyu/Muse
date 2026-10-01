package io.zer0.ai.core

/**
 * Builds a safe terminal event for a malformed SSE frame.
 *
 * The frame body may contain user prompts, tool arguments, or provider data, so
 * diagnostics expose only its length and keep the original throwable attached
 * for local logging/observability.
 */
internal fun malformedStreamFrameError(provider: String, data: String, throwable: Throwable?): ChatStreamEvent.Error =
    ChatStreamEvent.Error(
        message = "$provider 流式响应格式异常（frameLength=${data.length}），本轮请求已终止，请重试。",
        throwable = throwable,
    )

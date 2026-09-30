package io.zer0.muse.channel

import io.zer0.common.AppJson
import io.zer0.muse.tools.ToolRiskLevel
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * 渠道没有交互式审批链路:白名单损坏时 fail closed,且执行端只接受本轮实际暴露的非 HIGH 工具。
 */
internal fun parseChannelToolAllowlist(json: String): Set<String>? {
    if (json.isBlank()) return emptySet()
    return runCatching {
        AppJson.parseToJsonElement(json).jsonArray
            .mapNotNull { it.jsonPrimitive.contentOrNull }
            .toSet()
    }.getOrNull()
}

internal fun mayExecuteChannelToolCall(toolName: String, offeredToolNames: Set<String>, risk: ToolRiskLevel): Boolean {
    return toolName in offeredToolNames && risk != ToolRiskLevel.HIGH
}

package io.zer0.muse.tools

/**
 * 工具执行时由宿主链路注入的不可伪造上下文。
 *
 * 模型参数只描述“查什么”，不会获得修改记忆作用域的能力。
 */
data class ToolExecutionContext(
    val scope: String,
    val spaceId: String,
    val assistantId: String? = null,
    /** v2.x: 宿主会话 id(find_tools 动态装载等按会话生效的逻辑使用);旧调用点缺省 null。 */
    val sessionId: String? = null,
)

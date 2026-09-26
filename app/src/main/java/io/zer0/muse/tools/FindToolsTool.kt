package io.zer0.muse.tools

/**
 * v2.x 工具瘦身阶段3:find_tools 元工具 — 按需检索工具库。
 *
 * 默认分层收窄后,模型每轮只看到 CORE/STANDARD + 命中族 + 会话粘性工具;
 * 需要其他能力时,用本工具按关键词检索**全部已注册工具**(名称+描述),
 * 命中结果写入 [SessionToolLoadRegistry],后续轮次即可直接调用。
 *
 * 只读检索:不执行任何工具、不改权限;装载只是让工具"可见",审批照旧。
 */
object FindToolsTool {

    /** 工具名常量(供白名单特例等引用)。 */
    const val TOOL_NAME = "find_tools"

    private const val MAX_RESULTS = 8
    private const val DESCRIPTION_SNIPPET = 140

    fun toolDef() = ToolRegistry.ToolDef(
        name = TOOL_NAME,
        description = "Search the full tool library when you need a capability that is not in your current " +
            "tool list. Matching tools become available from your next call in this session. " +
            "Use short keywords, e.g. 'browser', 'file', 'calendar'.",
        parameters = mapOf(
            "query" to "Required. Keywords describing the capability you need (matched against tool name and description).",
        ),
        required = setOf("query"),
        category = "built-in",
        riskLevel = ToolRiskLevel.SAFE,
    )

    /**
     * 纯函数检索:按名称/描述关键词匹配,名称命中权重更高;名称命中优先排序。
     *
     * 关键词按空白/中英文逗号/分号拆分,长度 ≥2 的词参与匹配;整串均为单字符时用整串兜底。
     */
    fun search(
        query: String,
        tools: List<ToolRegistry.ToolDef>,
        excludeName: String = TOOL_NAME,
    ): List<ToolRegistry.ToolDef> {
        val raw = query.trim().lowercase()
        if (raw.isEmpty()) return emptyList()
        val terms = raw.split(Regex("[\\s,，、;；]+"))
            .filter { it.length >= 2 }
            .ifEmpty { listOf(raw) }
        return tools
            .asSequence()
            .filter { it.name != excludeName }
            .map { def ->
                val name = def.name.lowercase()
                val desc = def.description.lowercase()
                var score = 0
                terms.forEach { term ->
                    if (name.contains(term)) score += 3
                    if (desc.contains(term)) score += 1
                }
                def to score
            }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<ToolRegistry.ToolDef, Int>> { it.second }.thenBy { it.first.name })
            .map { it.first }
            .take(MAX_RESULTS)
            .toList()
    }

    /** 执行检索;命中工具记入会话装载表,返回给模型阅读的清单。 */
    suspend fun execute(
        args: Map<String, String>,
        toolRegistry: ToolRegistry,
        executionContext: ToolExecutionContext,
    ): String {
        val query = args["query"]?.trim().orEmpty()
        if (query.isEmpty()) return "Error: query parameter is required."
        val matches = search(query, toolRegistry.listTools())
        if (matches.isEmpty()) {
            return "No tools matched \"$query\". Try different or broader keywords."
        }
        executionContext.sessionId?.let { sessionId ->
            SessionToolLoadRegistry.markLoaded(sessionId, matches.map { it.name })
        }
        return buildString {
            appendLine("Matched ${matches.size} tool(s), now available from your next call in this session:")
            matches.forEach { def ->
                val snippet = def.description.replace('\n', ' ').take(DESCRIPTION_SNIPPET)
                appendLine("- ${def.name}: $snippet")
            }
        }.trimEnd()
    }
}

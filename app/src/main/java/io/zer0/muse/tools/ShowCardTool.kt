package io.zer0.muse.tools

/**
 * show_card 工具(既有实现 show-card-tool.ts 实现)。
 *
 * Agent 生成 HTML/SVG 卡片内容,在对话中内联渲染。
 * 实际渲染由 UI 侧的 CardRenderer 处理。
 */
object ShowCardTool {

    private var cardSeq = 0

    private fun generateCardId(): String {
        cardSeq += 1
        val ts = System.currentTimeMillis().toString(36)
        val seq = cardSeq.toString(36)
        return "c_${ts}_${seq}"
    }

    fun toolDef() = ToolRegistry.ToolDef(
        name = "show_card",
        description = "Show visual content (SVG graphics, diagrams, charts, or interactive HTML) " +
            "rendered inline as an interactive card in a sandboxed WebView. " +
            "Use for flowcharts, dashboards, data tables, timelines, or any spatial layout. " +
            "Do NOT include DOCTYPE, <html>, <head> or <body> tags — content fragments only.",
        parameters = mapOf(
            "title" to "Required. Short snake_case identifier (e.g. 'q4_revenue_chart').",
            "code" to "Required. HTML or SVG fragment. Use CSS variables for theming.",
            "data" to "Optional. JSON bound to this card; the card script reads it via " +
                "window.muse.getData(cardId). Later updates go through update_card_data.",
        ),
        required = setOf("title", "code"),
        category = "built-in",
        riskLevel = ToolRiskLevel.NORMAL,
    )

    fun execute(args: Map<String, String>, dataStore: io.zer0.muse.data.card.CardDataStore? = null): String {
        val title = args["title"]?.trim()
            ?: return "Error: title parameter is required."
        val code = args["code"]?.trim()
            ?: return "Error: code parameter is required."
        if (title.isEmpty() || code.isEmpty()) {
            return "Error: title and code cannot be empty."
        }
        val cardId = generateCardId()
        val data = args["data"]
        val bound = if (!data.isNullOrBlank() && dataStore != null) {
            dataStore.put(cardId, data)
            " Data bound: card script can read it via window.muse.getData(\"$cardId\")."
        } else {
            ""
        }
        return "Card '$title' rendered (id: $cardId). Code length: ${code.length} chars.$bound"
    }
}

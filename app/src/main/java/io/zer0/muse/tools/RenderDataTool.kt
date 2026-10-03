package io.zer0.muse.tools

import io.zer0.common.AppJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/**
 * Structured chart/table renderer for tool results.
 *
 * Charts are emitted as the existing ```card format and tables as bounded
 * Markdown. The chat tool-result renderer owns the visual presentation.
 */
object RenderDataTool {

    const val NAME = "render_data"

    private const val MAX_COLUMNS = 12
    private const val MAX_ROWS = 50
    private const val MAX_TEXT_LENGTH = 200
    private const val MAX_OUTPUT_LENGTH = 16_000

    private val CHART_TYPES = setOf("bar", "line", "donut")

    fun toolDef(): ToolRegistry.ToolDef =
        ToolRegistry.ToolDef(
            name = NAME,
            description =
                "在聊天中直接展示结构化数据。type=bar/line/donut 时 data 需为 " +
                    "{\"labels\":[...],\"values\":[...]}；type=table 时 data 需为 " +
                    "{\"columns\":[...],\"rows\":[[...]]}。数据仅在本地渲染，不访问网络。",
            parameters = mapOf(
                "type" to "必填:bar | line | donut | table",
                "title" to "必填,图表或表格标题",
                "data" to "必填 JSON 数据:图表使用 labels/values,表格使用 columns/rows",
            ),
            required = setOf("type", "title", "data"),
            category = "built-in",
            riskLevel = ToolRiskLevel.SAFE,
        )

    fun execute(args: Map<String, String>): String {
        val type = args["type"]?.trim()?.lowercase()
            ?: return error("缺少 type")
        val title = args["title"]?.let(::cleanText)
            ?: return error("缺少 title")
        if (title.isBlank()) return error("title 不能为空")
        val rawData = args["data"]?.trim()
            ?: return error("缺少 data")
        val data = runCatching { AppJson.parseToJsonElement(rawData) as? JsonObject }.getOrNull()
            ?: return error("data 必须是 JSON 对象")

        val rendered =
            when {
                type in CHART_TYPES -> renderChart(type, title, data)
                type == "table" -> renderTable(title, data)
                else -> error("type 只支持 bar、line、donut 或 table")
            }
        return if (rendered.length <= MAX_OUTPUT_LENGTH) rendered else error("输出超过最大长度")
    }

    private fun renderChart(type: String, title: String, data: JsonObject): String {
        val labels = stringArray(data["labels"]) ?: return error("图表 labels 必须是字符串数组")
        val values = numberArray(data["values"]) ?: return error("图表 values 必须是有限数字数组")
        if (labels.isEmpty() || values.isEmpty()) return error("图表数据不能为空")
        if (labels.size != values.size) return error("labels 与 values 长度必须一致")
        if (labels.size > MAX_ROWS) return error("图表数据最多支持 $MAX_ROWS 个点")

        val cardJson =
            buildJsonObject {
                put("type", JsonPrimitive(type))
                put("title", JsonPrimitive(title))
                put("labels", buildJsonArray { labels.forEach { add(JsonPrimitive(it)) } })
                put("values", buildJsonArray { values.forEach { add(JsonPrimitive(it)) } })
            }
        return "```card\n$cardJson\n```"
    }

    private fun renderTable(title: String, data: JsonObject): String {
        val columns = stringArray(data["columns"]) ?: return error("表格 columns 必须是字符串数组")
        if (columns.isEmpty()) return error("表格至少需要一列")
        if (columns.size > MAX_COLUMNS) return error("表格最多支持 $MAX_COLUMNS 列")
        val rows = data["rows"] as? JsonArray ?: return error("表格 rows 必须是二维数组")
        if (rows.size > MAX_ROWS) return error("表格最多支持 $MAX_ROWS 行")

        val normalizedRows =
            rows.mapIndexed { index, row ->
                val cells = stringArray(row) ?: return error("第 ${index + 1} 行必须是字符串/数字数组")
                if (cells.size != columns.size) {
                    return error("第 ${index + 1} 行列数与 columns 不一致")
                }
                cells
            }
        return buildString {
            appendLine("### $title")
            appendLine("| ${columns.joinToString(" | ")} |")
            appendLine("| ${columns.joinToString(" | ") { "---" }} |")
            normalizedRows.forEach { row ->
                appendLine("| ${row.joinToString(" | ")} |")
            }
        }.trimEnd()
    }

    private fun stringArray(element: kotlinx.serialization.json.JsonElement?): List<String>? {
        val array = element as? JsonArray ?: return null
        return array.map { primitive ->
            val value = (primitive as? JsonPrimitive)?.contentOrNull() ?: return null
            cleanText(value).takeIf { it.isNotBlank() } ?: return null
        }
    }

    private fun numberArray(element: kotlinx.serialization.json.JsonElement?): List<Double>? {
        val array = element as? JsonArray ?: return null
        return array.map { primitive ->
            val number = (primitive as? JsonPrimitive)?.content?.toDoubleOrNull() ?: return null
            number.takeIf { it.isFinite() } ?: return null
        }
    }

    private fun cleanText(value: String): String =
        value.replace('|', ' ').replace('\r', ' ').replace('\n', ' ').trim().take(MAX_TEXT_LENGTH)

    private fun error(message: String): String = "Error: $message"

    private fun JsonPrimitive.contentOrNull(): String? = content.takeIf { it.isNotBlank() }
}

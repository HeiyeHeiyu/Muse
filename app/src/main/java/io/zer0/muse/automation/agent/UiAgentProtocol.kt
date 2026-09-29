package io.zer0.muse.automation.agent

/**
 * v2.2.1 GUI Agent 环:动作协议解析(纯逻辑,可单测)。
 *
 * 视觉模型输出自由文本,协议取其中**最后一个** `do(...)` 或 `finish(...)` 表达式
 * (模型常先给推理再给结论)。参数形如 key=value,值支持单/双引号字符串(可含逗号)。
 */
object UiAgentProtocol {

    sealed class Action {
        data class Tap(val x: Int, val y: Int) : Action()
        data class Swipe(
            val x1: Int,
            val y1: Int,
            val x2: Int,
            val y2: Int,
            val durationMs: Long,
        ) : Action()

        data class TextInput(val text: String) : Action()
        data class Key(val key: String) : Action()
        data class Launch(val packageName: String) : Action()
        data class Wait(val ms: Long) : Action()
        data class Finish(val result: String) : Action()
    }

    private val DO_REGEX = Regex("""do\s*\(([^)]*)\)""", RegexOption.IGNORE_CASE)
    private val FINISH_REGEX = Regex("""finish\s*\(([^)]*)\)""", RegexOption.IGNORE_CASE)
    private val KV_REGEX = Regex("""([A-Za-z_][A-Za-z0-9_]*)\s*=\s*("[^"]*"|'[^']*'|[^,\s)]+)""")

    /** 解析模型输出;无有效动作返回 null。 */
    fun parse(raw: String): Action? {
        val doMatch = DO_REGEX.findAll(raw).lastOrNull()
        val finishMatch = FINISH_REGEX.findAll(raw).lastOrNull()
        if (doMatch == null && finishMatch == null) return null
        val useFinish = when {
            finishMatch == null -> false
            doMatch == null -> true
            else -> finishMatch.range.first > doMatch.range.first
        }
        return if (useFinish) parseFinish(finishMatch!!) else parseDo(doMatch!!)
    }

    /** 动作的人类可读描述(用于动作史回填)。 */
    fun describe(action: Action): String = when (action) {
        is Action.Tap -> "点击(${action.x},${action.y})"
        is Action.Swipe -> "滑动(${action.x1},${action.y1})→(${action.x2},${action.y2})"
        is Action.TextInput -> "输入\"${action.text.take(40)}\""
        is Action.Key -> "按键 ${action.key}"
        is Action.Launch -> "启动 ${action.packageName}"
        is Action.Wait -> "等待 ${action.ms}ms"
        is Action.Finish -> "结束"
    }

    private fun parseFinish(match: MatchResult): Action {
        val args = parseArgs(match.groupValues[1])
        val result = args["result"] ?: args["text"] ?: args["message"] ?: ""
        return Action.Finish(result)
    }

    private fun parseDo(match: MatchResult): Action? {
        val args = parseArgs(match.groupValues[1])
        val type = (args["action"] ?: args["type"] ?: args["name"] ?: return null).lowercase()
        return when (type) {
            "tap", "click" -> {
                val x = args["x"]?.toIntOrNull() ?: return null
                val y = args["y"]?.toIntOrNull() ?: return null
                Action.Tap(x, y)
            }

            "swipe" -> {
                val x1 = args["x1"]?.toIntOrNull() ?: return null
                val y1 = args["y1"]?.toIntOrNull() ?: return null
                val x2 = args["x2"]?.toIntOrNull() ?: return null
                val y2 = args["y2"]?.toIntOrNull() ?: return null
                val duration = (args["duration"] ?: args["duration_ms"])
                    ?.toLongOrNull()?.coerceIn(50L, 5_000L) ?: 400L
                Action.Swipe(x1, y1, x2, y2, duration)
            }

            "text", "input", "type" ->
                Action.TextInput(args["text"] ?: args["content"] ?: return null)

            "key", "keyevent" ->
                Action.Key((args["key"] ?: args["name"] ?: return null).lowercase())

            "launch", "open_app" ->
                Action.Launch(args["package"] ?: args["pkg"] ?: args["app"] ?: return null)

            "wait", "sleep" -> {
                val ms = (args["ms"] ?: args["duration"] ?: "800")
                    .toLongOrNull()?.coerceIn(50L, 10_000L) ?: 800L
                Action.Wait(ms)
            }

            else -> null
        }
    }

    /** 提取 `key=value` 参数;引号值去除引号,无引号值读到空白/逗号/右括号为止。 */
    private fun parseArgs(body: String): Map<String, String> = KV_REGEX.findAll(body).associate { m ->
        val rawValue = m.groupValues[2]
        val value = when {
            rawValue.length >= 2 && rawValue.startsWith("\"") && rawValue.endsWith("\"") ->
                rawValue.substring(1, rawValue.length - 1)
            rawValue.length >= 2 && rawValue.startsWith("'") && rawValue.endsWith("'") ->
                rawValue.substring(1, rawValue.length - 1)
            else -> rawValue
        }
        m.groupValues[1].lowercase() to value
    }
}

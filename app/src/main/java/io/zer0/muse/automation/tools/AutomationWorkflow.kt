package io.zer0.muse.automation.tools

import io.zer0.common.AppJson
import io.zer0.muse.automation.appcontrol.AppControlCore
import io.zer0.muse.automation.core.AutomationManager
import io.zer0.muse.tools.ToolOutcome
import io.zer0.muse.tools.WorkflowJournal
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/**
 * 受控设备工作流 —— 把安全的手机动作原语组合成一次可审计调用。
 *
 * 这是 Agent 编排层的第一块：动作集合是显式白名单，不接受任意 shell；每一步失败就停止，
 * 结果同时保留给模型的摘要和给编排/UI 使用的结构化 details。
 */
class AutomationWorkflow(
    private val manager: AutomationManager,
    private val journal: WorkflowJournal? = null,
) {
    suspend fun run(steps: List<AutomationWorkflowStep>, runId: String): ToolOutcome {
        val results = mutableListOf<AutomationWorkflowStepResult>()
        val expectedKeys = steps.mapIndexed { index, step ->
            val prompt = AppJson.encodeToString(AutomationWorkflowStep.serializer(), step)
            index to (journal?.computeKey(prompt, "automation_workflow:$index") ?: "$index:${step.action}")
        }.toMap()
        val resume = journal?.resume(runId, expectedKeys)
        val resumeFrom = resume?.resumeFromSeq ?: 0
        for ((index, step) in steps.withIndex()) {
            val cached = resume?.cached?.get(index)
            val cachedResult = cached
                ?.takeIf { index < resumeFrom && it.status == "done" }
                ?.let { runCatching { AppJson.decodeFromString(AutomationWorkflowStepResult.serializer(), it.result) }.getOrNull() }
            val result = cachedResult?.copy(message = "已从工作流断点恢复") ?: executeStep(index, step)
            results += result
            journal?.record(
                runId = runId,
                nodeSeq = index,
                key = expectedKeys[index].orEmpty(),
                result = AppJson.encodeToString(AutomationWorkflowStepResult.serializer(), result),
                status = if (result.success) "done" else "failed",
                nodeKind = WorkflowJournal.NODE_KIND_TOOL_ONLY,
            )
            if (!result.success) break
        }
        val completed = results.count { it.success }
        val success = results.size == steps.size && completed == steps.size
        val content = buildString {
            append(if (success) "工作流完成" else "工作流在第 ${completed + 1} 步停止")
            append("($completed/${steps.size})")
            results.forEach { item ->
                appendLine()
                append("[${item.index}] ${item.action}: ${item.message}")
            }
        }
        return if (success) {
            ToolOutcome.ok(
                content,
                mapOf("runId" to runId, "resumeFrom" to resumeFrom, "completed" to completed, "total" to steps.size, "steps" to results),
            )
        } else {
            ToolOutcome.error(
                content,
                mapOf("runId" to runId, "resumeFrom" to resumeFrom, "completed" to completed, "total" to steps.size, "steps" to results),
            )
        }
    }

    private suspend fun executeStep(index: Int, step: AutomationWorkflowStep): AutomationWorkflowStepResult {
        val action = step.action.trim().lowercase()
        return runCatching {
            when (action) {
                "launch" -> executeLaunch(index, action, step)
                "tap_text" -> executeTapText(index, action, step)
                "input_text" -> executeInputText(index, action, step)
                "swipe" -> executeSwipe(index, action, step)
                "back" -> result(index, action, manager.back(), "返回")
                "home" -> result(index, action, manager.home(), "回到桌面")
                "wait" -> executeWait(index, action, step)
                "read" -> executeRead(index, action)
                else -> failure(index, action, "不支持的动作")
            }
        }.getOrElse { error -> failure(index, action, "执行异常:${error.message ?: "unknown"}") }
    }

    private suspend fun executeLaunch(index: Int, action: String, step: AutomationWorkflowStep): AutomationWorkflowStepResult {
        val pkg = step.packageName?.trim().orEmpty()
        return if (!AppControlCore.isValidPackageName(pkg)) {
            failure(index, action, "包名无效")
        } else {
            result(index, action, manager.launchApp(pkg), "启动 $pkg")
        }
    }

    private suspend fun executeTapText(index: Int, action: String, step: AutomationWorkflowStep): AutomationWorkflowStepResult {
        val text = step.text?.takeIf { it.isNotBlank() } ?: return failure(index, action, "缺少 text")
        val outcome = manager.tapByTextWithRetryDetailed(
            text = text,
            exact = step.exact,
            maxSwipes = step.maxSwipes,
            verifyText = step.verifyText?.takeIf { it.isNotBlank() },
        )
        return result(index, action, outcome.success, "匹配=${outcome.matched},验证=${outcome.verified},尝试=${outcome.attempts}")
    }

    private suspend fun executeInputText(index: Int, action: String, step: AutomationWorkflowStep): AutomationWorkflowStepResult {
        val text = step.text ?: return failure(index, action, "缺少 text")
        return result(index, action, manager.inputText(text), "输入 ${text.take(40)}")
    }

    private suspend fun executeSwipe(index: Int, action: String, step: AutomationWorkflowStep): AutomationWorkflowStepResult {
        val points = listOf(step.x1, step.y1, step.x2, step.y2)
        return if (points.any { it == null }) {
            failure(index, action, "缺少 x1/y1/x2/y2")
        } else {
            result(
                index,
                action,
                manager.swipe(step.x1!!, step.y1!!, step.x2!!, step.y2!!, step.durationMs.coerceIn(50L, 5_000L)),
                "滑动",
            )
        }
    }

    private suspend fun executeWait(index: Int, action: String, step: AutomationWorkflowStep): AutomationWorkflowStepResult {
        val ms = step.durationMs.coerceIn(50L, 10_000L)
        delay(ms)
        return result(index, action, true, "等待 ${ms}ms")
    }

    private suspend fun executeRead(index: Int, action: String): AutomationWorkflowStepResult {
        val screen = manager.readScreen()
        return result(index, action, true, screen.toSummary(20))
    }

    private fun result(index: Int, action: String, success: Boolean, message: String) =
        AutomationWorkflowStepResult(index, action, success, message)

    private fun failure(index: Int, action: String, message: String) = AutomationWorkflowStepResult(index, action, false, message)
}

@Serializable
data class AutomationWorkflowStep(
    val action: String,
    val packageName: String? = null,
    val text: String? = null,
    val verifyText: String? = null,
    val exact: Boolean = false,
    val maxSwipes: Int = 0,
    val x1: Int? = null,
    val y1: Int? = null,
    val x2: Int? = null,
    val y2: Int? = null,
    val durationMs: Long = 400L,
)

@Serializable
data class AutomationWorkflowStepResult(
    val index: Int,
    val action: String,
    val success: Boolean,
    val message: String,
)

internal object AutomationWorkflowParser {
    private const val MAX_STEPS = 20

    fun parse(json: String): Result<List<AutomationWorkflowStep>> = runCatching {
        val steps = AppJson.decodeFromString(
            ListSerializer(AutomationWorkflowStep.serializer()),
            json,
        )
        require(steps.isNotEmpty()) { "steps 不能为空" }
        require(steps.size <= MAX_STEPS) { "steps 最多支持 $MAX_STEPS 步" }
        steps
    }
}

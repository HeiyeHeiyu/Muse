package io.zer0.muse.automation.agent

import io.zer0.muse.tools.ToolRegistry
import io.zer0.muse.tools.ToolRiskLevel

/**
 * v2.2.1: 把 GUI Agent 环注册为 `ui_agent` 工具(HIGH,强制审批)。
 */
class UiAgentTool(private val runner: UiAgentRunner) {

    fun register(registry: ToolRegistry) {
        registry.register(
            ToolRegistry.ToolDef(
                name = "ui_agent",
                description = "GUI Agent 环:用视觉模型看着屏幕,一步步操作手机完成跨 App 的多步任务" +
                    "(如\"打开微信给张三发消息\")。内部自动循环:截图→视觉决策→点击/滑动/输入,默认最多 15 步。" +
                    "需要视觉辅助已配置(设置→视觉辅助)且无障碍/Shizuku/Root 任一通道可用。" +
                    "单步操作请直接用 screen_* 工具,多步流程再用本工具。",
                parameters = mapOf(
                    "task" to "必填。要完成的任务描述,尽量具体(目标 App、对象、操作意图)",
                    "max_steps" to "可选。最大步数 3-30,默认 15",
                ),
                required = setOf("task"),
                riskLevel = ToolRiskLevel.HIGH,
            ),
        ) { args ->
            val task = args["task"]?.trim().orEmpty()
            if (task.isBlank()) return@register "错误:缺少 task 参数"
            val maxSteps = args["max_steps"]?.toIntOrNull() ?: UiAgentRunner.DEFAULT_MAX_STEPS
            val result = runner.run(task, maxSteps)
            buildString {
                append(if (result.finished) "任务结束:" else "任务未完成:")
                append('\n')
                append(result.summary)
            }
        }
    }
}

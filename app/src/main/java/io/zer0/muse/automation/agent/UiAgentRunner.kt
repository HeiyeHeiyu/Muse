package io.zer0.muse.automation.agent

import android.graphics.BitmapFactory
import android.util.Base64
import io.zer0.common.Logger
import io.zer0.muse.automation.core.AutomationManager
import io.zer0.muse.automation.core.DeviceCommandPolicy
import io.zer0.muse.vision.VisionAnalysisException
import io.zer0.muse.vision.VisionBridge
import io.zer0.muse.vision.VisionImagePreprocessor
import kotlinx.coroutines.delay

/**
 * v2.2.1 GUI Agent 环(Operit 借鉴):视觉模型看着屏幕,一步步操作设备完成跨 App 任务。
 *
 * 每步循环:截屏 → 预处理(压缩到预算内) → 视觉模型输出 `do(...)`/`finish(...)`
 * → 解析并执行 → 记入动作史。**历史截图不进入下一轮提示词**(只回填动作史文本)——
 * 截图瘦身,把视觉 token 与主对话上下文都锁在环内。
 *
 * 动作执行复用 [AutomationManager] 分层通道(无障碍/Shizuku/Root),按键类命令
 * 走 [DeviceCommandPolicy] 白名单校验后执行。
 */
class UiAgentRunner(
    private val manager: AutomationManager,
    private val vision: VisionBridge,
) {

    data class RunResult(val finished: Boolean, val summary: String)

    /**
     * 运行任务。
     * @param task 任务描述(自然语言)
     * @param maxSteps 最大步数(3..[HARD_MAX_STEPS],默认 15)
     * @param onProgress 每步进度回调(IO 线程)
     */
    suspend fun run(task: String, maxSteps: Int = DEFAULT_MAX_STEPS, onProgress: (String) -> Unit = {}): RunResult {
        val steps = maxSteps.coerceIn(3, HARD_MAX_STEPS)
        val history = StringBuilder()
        repeat(steps) { index ->
            val stepNo = index + 1
            val shot = manager.screenshot()
                ?: return RunResult(
                    false,
                    "无法截屏(需要 Shizuku / Root 或无障碍通道;可先用 screen_permission_status 检查)。\n$history",
                )
            val prepared = VisionImagePreprocessor.prepareSingle(
                Base64.encodeToString(shot, Base64.NO_WRAP),
            ) ?: return RunResult(false, "截图预处理失败。\n$history")

            val (width, height) = screenSize(shot)
            val answer = try {
                vision.askWithImage(
                    prompt = buildPrompt(task, stepNo, steps, history.toString(), width, height),
                    imageBase64 = prepared.base64,
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: VisionAnalysisException) {
                Logger.w(TAG, "视觉决策失败: ${e.message}")
                return RunResult(false, "视觉决策失败:${e.message}\n$history")
            } catch (e: Exception) {
                Logger.w(TAG, "视觉决策异常: ${e.message}", e)
                return RunResult(false, "视觉决策异常:${e.message}\n$history")
            }

            val action = UiAgentProtocol.parse(answer)
            if (action == null) {
                history.append("[$stepNo] 模型输出无法解析,已跳过\n")
                onProgress("第 $stepNo 步:输出不可解析,跳过")
                return@repeat
            }
            if (action is UiAgentProtocol.Action.Finish) {
                onProgress("第 $stepNo 步:结束")
                val result = action.result.ifBlank { "任务完成(模型未附说明)" }
                return RunResult(true, "$result\n---\n动作记录:\n$history")
            }

            val outcome = execute(action)
            history.append("[$stepNo] ${UiAgentProtocol.describe(action)} -> $outcome\n")
            onProgress("第 $stepNo 步:${UiAgentProtocol.describe(action)} -> $outcome")
            // 给界面一点响应时间(连续快速动作常导致下一次截屏还是旧状态)
            delay(STEP_SETTLE_MS)
        }
        return RunResult(false, "达到最大步数($steps),任务可能未完成。\n动作记录:\n$history")
    }

    // ── 动作执行 ─────────────────────────────────────────────────────────

    private suspend fun execute(action: UiAgentProtocol.Action): String = when (action) {
        is UiAgentProtocol.Action.Tap ->
            if (manager.tap(action.x, action.y)) "ok" else "失败(自动化通道未就绪)"

        is UiAgentProtocol.Action.Swipe ->
            if (manager.swipe(action.x1, action.y1, action.x2, action.y2, action.durationMs)) {
                "ok"
            } else {
                "失败(自动化通道未就绪)"
            }

        is UiAgentProtocol.Action.TextInput ->
            if (manager.inputText(action.text)) "ok" else "失败(输入框可能未聚焦)"

        is UiAgentProtocol.Action.Key -> keyEvent(action.key)

        is UiAgentProtocol.Action.Launch ->
            if (manager.launchApp(action.packageName)) "ok" else "失败(包名可能不正确)"

        is UiAgentProtocol.Action.Wait -> {
            delay(action.ms)
            "ok"
        }

        is UiAgentProtocol.Action.Finish -> "ok"
    }

    private suspend fun keyEvent(key: String): String = when (key) {
        "back" -> if (manager.back()) "ok" else "失败(通道未就绪)"
        "home" -> if (manager.home()) "ok" else "失败(通道未就绪)"
        "enter" -> execKeyevent(66)
        "recents", "app_switch" -> execKeyevent(187)
        else -> "未知按键:$key"
    }

    private suspend fun execKeyevent(code: Int): String {
        val cmd = "input keyevent $code"
        return when (val check = DeviceCommandPolicy.validate(cmd)) {
            is DeviceCommandPolicy.Check.Invalid -> "命令被拒绝:${check.reason}"
            DeviceCommandPolicy.Check.Valid ->
                if (manager.execTiered(cmd) != null) "ok" else "失败(通道未就绪)"
        }
    }

    // ── 视觉提示词 ───────────────────────────────────────────────────────

    private fun buildPrompt(task: String, step: Int, maxSteps: Int, history: String, width: Int, height: Int): String = buildString {
        appendLine("你是手机操作代理:查看截图,为完成用户任务决定下一个动作。")
        appendLine("任务:$task")
        appendLine("这是第 $step/$maxSteps 步。屏幕分辨率:${width}x$height(坐标直接使用该分辨率的像素值)。")
        appendLine("已完成动作:")
        appendLine(history.ifBlank { "(无)" }.trimEnd())
        appendLine("可选动作(严格输出其中一条,不要解释):")
        appendLine("do(action=\"tap\", x=540, y=1200)")
        appendLine("do(action=\"swipe\", x1=540, y1=1500, x2=540, y2=600, duration=400)")
        appendLine("do(action=\"text\", text=\"要输入的内容\")")
        appendLine("do(action=\"key\", key=\"back\")")
        appendLine("do(action=\"launch\", package=\"com.example.app\")")
        appendLine("do(action=\"wait\", ms=800)")
        appendLine("finish(result=\"任务完成情况说明\")")
        append("要求:只看最新截图;坐标不要越界;若上一步动作没有生效,换一种方式;任务完成或确实无法继续时输出 finish。")
    }

    private fun screenSize(png: ByteArray): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(png, 0, png.size, options)
        return options.outWidth to options.outHeight
    }

    companion object {
        private const val TAG = "UiAgentRunner"
        const val DEFAULT_MAX_STEPS = 15
        const val HARD_MAX_STEPS = 30
        private const val STEP_SETTLE_MS = 500L
    }
}

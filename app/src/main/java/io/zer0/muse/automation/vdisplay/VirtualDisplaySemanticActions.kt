package io.zer0.muse.automation.vdisplay

import io.zer0.muse.automation.core.AutomationManager
import io.zer0.muse.automation.core.DeviceCommandPolicy
import kotlinx.coroutines.delay

/** Bounded, display-scoped semantic actions shared by the visual Agent and durable workflows. */
object VirtualDisplaySemanticActions {
    data class TapTextResult(
        val success: Boolean,
        val attempts: Int,
        val matched: Boolean,
        val verified: Boolean,
        val outcomeUnknown: Boolean = false,
        val error: String? = null,
    )

    suspend fun tapText(
        manager: AutomationManager,
        displayManager: VirtualDisplayServerManager,
        displayId: Int,
        text: String,
        exact: Boolean = false,
        maxSwipes: Int = 0,
        verifyText: String? = null,
    ): TapTextResult {
        if (text.isBlank()) return TapTextResult(false, 0, false, false, error = "缺少 text")
        val boundedSwipes = maxSwipes.coerceIn(0, MAX_SWIPES)
        repeat(boundedSwipes + 1) { attempt ->
            val screen = manager.readScreenOnDisplay(displayId)
                ?: return TapTextResult(false, attempt + 1, false, false, error = "虚拟屏控件树不可用")
            val node = screen.findBestTextNode(text, exact)
            if (node != null) {
                val x = node.centerX
                val y = node.centerY
                if (screen.screenWidth <= 0 || screen.screenHeight <= 0 || x !in 0 until screen.screenWidth || y !in 0 until screen.screenHeight) {
                    return TapTextResult(false, attempt + 1, true, false, error = "目标控件坐标无效")
                }
                val tap = VirtualDisplayInputCommand.build(displayId, "tap", mapOf("x" to x.toString(), "y" to y.toString()))
                    ?: return TapTextResult(false, attempt + 1, true, false, error = "无法构造虚拟屏点击")
                when (val check = DeviceCommandPolicy.validate(tap.shellCommand)) {
                    is DeviceCommandPolicy.Check.Invalid ->
                        return TapTextResult(false, attempt + 1, true, false, error = "点击命令被策略拒绝:${check.reason}")
                    DeviceCommandPolicy.Check.Valid -> Unit
                }
                val execution = displayManager.exec(tap.shellCommand)
                if (execution.exitCode != 0) {
                    return TapTextResult(false, attempt + 1, true, false, outcomeUnknown = true, error = "虚拟屏点击结果未知(exit=${execution.exitCode})")
                }
                val expected = verifyText?.trim()?.takeIf { it.isNotEmpty() }
                    ?: return TapTextResult(true, attempt + 1, true, false)
                delay(350L)
                val after = manager.readScreenOnDisplay(displayId)
                    ?: return TapTextResult(false, attempt + 1, true, false, outcomeUnknown = true, error = "点击已发送但无法读取验证界面")
                val verified = after.nodes.any { candidate ->
                    val label = candidate.text ?: candidate.contentDescription ?: return@any false
                    label.contains(expected, ignoreCase = true)
                }
                return if (verified) {
                    TapTextResult(true, attempt + 1, true, true)
                } else {
                    TapTextResult(false, attempt + 1, true, false, outcomeUnknown = true, error = "点击已发送但未观察到预期界面")
                }
            }
            if (attempt < boundedSwipes) {
                if (screen.screenWidth <= 0 || screen.screenHeight <= 0) {
                    return TapTextResult(false, attempt + 1, false, false, error = "虚拟屏分辨率无效，无法滚动")
                }
                val swipe = VirtualDisplayInputCommand.build(
                    displayId,
                    "swipe",
                    mapOf(
                        "x1" to (screen.screenWidth / 2).toString(),
                        "y1" to (screen.screenHeight * 82 / 100).toString(),
                        "x2" to (screen.screenWidth / 2).toString(),
                        "y2" to (screen.screenHeight * 28 / 100).toString(),
                        "duration_ms" to SWIPE_DURATION_MS.toString(),
                    ),
                ) ?: return TapTextResult(false, attempt + 1, false, false, error = "无法构造虚拟屏滚动")
                when (val check = DeviceCommandPolicy.validate(swipe.shellCommand)) {
                    is DeviceCommandPolicy.Check.Invalid ->
                        return TapTextResult(false, attempt + 1, false, false, error = "滚动命令被策略拒绝:${check.reason}")
                    DeviceCommandPolicy.Check.Valid -> Unit
                }
                if (displayManager.exec(swipe.shellCommand).exitCode != 0) {
                    return TapTextResult(false, attempt + 1, false, false, error = "虚拟屏滚动失败")
                }
                delay(SCROLL_SETTLE_MS)
            }
        }
        return TapTextResult(false, boundedSwipes + 1, false, false, error = "有界滚动后仍未找到目标")
    }

    suspend fun tapViewId(
        manager: AutomationManager,
        displayManager: VirtualDisplayServerManager,
        displayId: Int,
        viewId: String,
        maxSwipes: Int = 0,
        verifyText: String? = null,
    ): TapTextResult {
        if (viewId.isBlank()) return TapTextResult(false, 0, false, false, error = "缺少 view_id")
        val bounded = maxSwipes.coerceIn(0, MAX_SWIPES)
        repeat(bounded + 1) { attempt ->
            val screen = manager.readScreenOnDisplay(displayId)
                ?: return TapTextResult(false, attempt + 1, false, false, error = "虚拟屏控件树不可用")
            val node = screen.findBestViewIdNode(viewId)
            if (node != null) {
                val x = node.centerX; val y = node.centerY
                if (screen.screenWidth <= 0 || screen.screenHeight <= 0 || x !in 0 until screen.screenWidth || y !in 0 until screen.screenHeight) {
                    return TapTextResult(false, attempt + 1, true, false, error = "目标控件坐标无效")
                }
                val command = VirtualDisplayInputCommand.build(displayId, "tap", mapOf("x" to x.toString(), "y" to y.toString()))
                    ?: return TapTextResult(false, attempt + 1, true, false, error = "无法构造虚拟屏点击")
                when (val check = DeviceCommandPolicy.validate(command.shellCommand)) {
                    is DeviceCommandPolicy.Check.Invalid -> return TapTextResult(false, attempt + 1, true, false, error = "点击命令被策略拒绝:${check.reason}")
                    DeviceCommandPolicy.Check.Valid -> Unit
                }
                if (displayManager.exec(command.shellCommand).exitCode != 0) {
                    return TapTextResult(false, attempt + 1, true, false, outcomeUnknown = true, error = "虚拟屏点击结果未知")
                }
                val expected = verifyText?.takeIf { it.isNotBlank() } ?: return TapTextResult(true, attempt + 1, true, true)
                delay(350L)
                val after = manager.readScreenOnDisplay(displayId) ?: return TapTextResult(false, attempt + 1, true, false, outcomeUnknown = true, error = "点击已发送但无法验证")
                val verified = after.nodes.any { candidate ->
                    val text = candidate.text ?: candidate.contentDescription ?: return@any false
                    text.contains(expected, ignoreCase = true)
                }
                return if (verified) TapTextResult(true, attempt + 1, true, true) else TapTextResult(false, attempt + 1, true, false, outcomeUnknown = true, error = "点击已发送但未验证")
            }
            if (attempt < bounded && screen.screenHeight > 0) {
                val swipe = VirtualDisplayInputCommand.build(displayId, "swipe", mapOf(
                    "x1" to (screen.screenWidth / 2).toString(), "y1" to (screen.screenHeight * 82 / 100).toString(),
                    "x2" to (screen.screenWidth / 2).toString(), "y2" to (screen.screenHeight * 28 / 100).toString(),
                    "duration_ms" to SWIPE_DURATION_MS.toString(),
                )) ?: return TapTextResult(false, attempt + 1, false, false, error = "无法构造滚动")
                if (displayManager.exec(swipe.shellCommand).exitCode != 0) return TapTextResult(false, attempt + 1, false, false, error = "滚动失败")
                delay(SCROLL_SETTLE_MS)
            }
        }
        return TapTextResult(false, bounded + 1, false, false, error = "有界滚动后未找到 view_id")
    }

    private const val MAX_SWIPES = 5
    private const val SWIPE_DURATION_MS = 450L
    private const val SCROLL_SETTLE_MS = 300L
}

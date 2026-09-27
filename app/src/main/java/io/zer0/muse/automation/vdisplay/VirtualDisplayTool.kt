package io.zer0.muse.automation.vdisplay

import android.content.Context
import io.zer0.muse.tools.ToolRegistry
import io.zer0.muse.tools.ToolRiskLevel
import java.io.File

/**
 * v2.2.1 虚拟屏:AI 工具 `virtual_screen`。
 *
 * 把虚拟屏能力注册为工具:ensure(建屏)/ open(屏内开应用)/ shot(截图)/
 * close(销毁)/ status(状态)。虚拟屏内的输入注入走 device_shell 的
 * `input -d <displayId> ...`(命令策略已放行)。
 */
class VirtualDisplayTool(
    private val context: Context,
    private val client: VirtualDisplayClient,
    private val manager: VirtualDisplayServerManager,
) {
    fun register(registry: ToolRegistry) {
        registry.register(
            ToolRegistry.ToolDef(
                name = "virtual_screen",
                description =
                    "虚拟屏(后台隐藏屏幕):在独立显示里运行应用,截图/操作不占用前台、不打扰用户。" +
                        "action=ensure 创建或复用虚拟屏(返回 displayId);action=open 在虚拟屏里打开应用(需 package);" +
                        "action=shot 截取虚拟屏画面(保存为 JPEG 文件,返回路径);action=close 销毁虚拟屏;" +
                        "action=status 查询状态。在虚拟屏里点按/滑动/按键:用 device_shell 执行 " +
                        "`input -d <displayId> tap/swipe/keyevent ...`。需要 Shizuku 权限通道(设置→权限向导)。",
                parameters =
                    mapOf(
                        "action" to "必填:ensure | open | shot | close | status",
                        "package" to "open 时的应用包名(如 com.android.settings)",
                        "width" to "可选:虚拟屏宽度(默认 720)",
                        "height" to "可选:虚拟屏高度(默认 1280)",
                        "dpi" to "可选:虚拟屏密度(默认 320)",
                        "display" to "可选:指定 displayId(默认最近一次 ensure 的)",
                    ),
                required = setOf("action"),
                riskLevel = ToolRiskLevel.NORMAL,
            ),
        ) { args ->
            when (val action = args["action"]?.trim()?.lowercase()) {
                "ensure" -> handleEnsure(args)
                "open" -> handleOpen(args)
                "shot" -> handleShot(args)
                "close" -> handleClose(args)
                "status" -> handleStatus()
                null, "" -> "错误:缺少 action 参数(ensure/open/shot/close/status)"
                else -> "错误:未知 action '$action'(ensure/open/shot/close/status)"
            }
        }
    }

    private suspend fun handleEnsure(args: Map<String, String>): String {
        val width = args["width"]?.toIntOrNull() ?: 720
        val height = args["height"]?.toIntOrNull() ?: 1280
        val dpi = args["dpi"]?.toIntOrNull() ?: 320
        if (width !in 320..1920 || height !in 320..2560) {
            return "错误:尺寸超范围(宽 320-1920,高 320-2560)"
        }
        return client.ensureDisplay(width, height, dpi).fold(
            onSuccess = {
                "虚拟屏已就绪:displayId=${it.displayId}(${it.width}x${it.height})。" +
                    "用 virtual_screen open 打开应用,用 device_shell `input -d ${it.displayId} ...` 做输入。"
            },
            onFailure = { "虚拟屏创建失败:${it.message}" },
        )
    }

    private suspend fun handleOpen(args: Map<String, String>): String {
        val pkg = args["package"]?.trim().orEmpty()
        if (pkg.isBlank()) return "错误:open 需要 package 参数"
        val displayId = args["display"]?.toIntOrNull() ?: manager.lastDisplayId
        if (displayId < 0) {
            val ensured = handleEnsure(args)
            if (!ensured.startsWith("虚拟屏已就绪")) return ensured
        }
        val id = if (displayId >= 0) displayId else manager.lastDisplayId
        return if (client.openApp(pkg, id)) {
            "已在虚拟屏 $id 打开 $pkg(等待 1-2 秒渲染后可用 shot 截图)"
        } else {
            "打开失败:无法解析 $pkg 的启动活动(应用未安装或被系统限制)"
        }
    }

    private suspend fun handleShot(args: Map<String, String>): String {
        val displayId = args["display"]?.toIntOrNull() ?: manager.lastDisplayId
        val bytes =
            client.screenshot(displayId)
                ?: return "截图失败:虚拟屏未创建或服务端未就绪(先执行 action=ensure)"
        return runCatching {
            val dir = File(context.filesDir, "vd").apply { mkdirs() }
            val file = File(dir, "shot_${System.currentTimeMillis()}.jpg")
            file.writeBytes(bytes)
            "截图已保存:${file.absolutePath}(${bytes.size / 1024}KB,displayId=$displayId)"
        }.getOrElse { "截图保存失败:${it.message}" }
    }

    private suspend fun handleClose(args: Map<String, String>): String {
        val displayId = args["display"]?.toIntOrNull() ?: manager.lastDisplayId
        return if (client.destroy(displayId)) "虚拟屏 $displayId 已销毁" else "没有可销毁的虚拟屏"
    }

    private suspend fun handleStatus(): String {
        val proxy = manager.existingLiveProxy()
        val channel = manager.channelState()
        return buildString {
            appendLine("Shizuku 通道:$channel")
            appendLine("服务端:${if (proxy != null) "在线" else "未运行(首次调用自动启动)"}")
            append("虚拟屏:${if (manager.lastDisplayId >= 0) "displayId=${manager.lastDisplayId}" else "未创建"}")
        }
    }
}

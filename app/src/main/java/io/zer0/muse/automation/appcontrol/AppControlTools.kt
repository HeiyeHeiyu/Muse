package io.zer0.muse.automation.appcontrol

import android.content.Context
import io.zer0.muse.R
import io.zer0.muse.tools.ToolRegistry
import io.zer0.muse.tools.ToolRiskLevel

/**
 * 应用控制工具集 —— 把 [AndroidAppController] 暴露为 AI 可调用的工具。
 *
 * 命名前缀统一 `app_`，与 `screen_*`（UI 自动化）、`device_*`（设备命令）并列：
 *  - `app_list`        列出可启动应用（无授权要求）
 *  - `app_launch`      启动应用（无授权要求）
 *  - `app_settings`    打开应用系统设置页（无授权要求，用户可自行改权限/清数据）
 *  - `app_force_stop`  强行停止（需 Shizuku/adb 或 Root）
 *  - `app_clear_data`  清空数据（需 Shizuku/adb 或 Root）
 *  - `app_uninstall`   卸载（需 Shizuku/adb 或 Root）
 *
 * 注册分两组：**感知/应用层**（NORMAL，无需授权）与**破坏性**（HIGH，走审批）——
 * 分组不只是为了函数长度，更让"哪些工具不该走审批、哪些必须走"一眼可辨。
 * 缺通道时**如实返回"需要哪一项能力"**，不谎报成功——这是 Agent 可信度的前提。
 */
class AppControlTools(
    private val context: Context,
    private val controller: AndroidAppController,
) {
    fun register(registry: ToolRegistry) {
        registerInspectionTools(registry)
        registerDestructiveTools(registry)
    }

    /** 感知与应用层动作：只需应用自身权限，不需要任何自动化授权。 */
    private fun registerInspectionTools(registry: ToolRegistry) {
        registry.register(
            ToolRegistry.ToolDef(
                name = "app_list",
                description = "列出本机可启动的应用（名称 + 包名）。当用户用中文名提到某个应用、" +
                    "而你不确定它的包名时，先用本工具查到包名，再调用 app_launch 等操作。",
                parameters = mapOf(
                    "query" to "可选，按名称或包名过滤，如「微信」",
                ),
                required = emptySet(),
                riskLevel = ToolRiskLevel.NORMAL,
            ),
        ) { args ->
            val apps = controller.listLaunchableApps()
            val query = args["query"]?.trim().orEmpty()
            val filtered = if (query.isBlank()) {
                apps
            } else {
                apps.filter {
                    it.label.contains(query, ignoreCase = true) ||
                        it.packageName.contains(query, ignoreCase = true)
                }
            }
            AppControlCore.renderAppList(filtered)
        }

        registry.register(
            ToolRegistry.ToolDef(
                name = "app_launch",
                description = "启动一个应用（按包名或中文名）。启动成功后应调用 screen_read 确认界面已就绪。",
                parameters = mapOf("app" to "必填，包名或应用名，如 com.tencent.mm 或「微信」"),
                required = setOf("app"),
                riskLevel = ToolRiskLevel.NORMAL,
            ),
        ) { args ->
            runAction(AppControlAction.LAUNCH, R.string.app_control_launch_action, args["app"])
        }

        registry.register(
            ToolRegistry.ToolDef(
                name = "app_settings",
                description = "打开某个应用的系统设置详情页（可查看/修改权限、通知、存储占用）。" +
                    "此类操作适合让用户自己确认，比直接清数据更安全。",
                parameters = mapOf("app" to "必填，包名或应用名"),
                required = setOf("app"),
                riskLevel = ToolRiskLevel.NORMAL,
            ),
        ) { args ->
            runAction(AppControlAction.OPEN_APP_SETTINGS, R.string.app_control_settings_action, args["app"])
        }
    }

    /** 破坏性动作：需要 Shizuku/adb 或 Root，风险 HIGH（走审批）。 */
    private fun registerDestructiveTools(registry: ToolRegistry) {
        registry.register(
            ToolRegistry.ToolDef(
                name = "app_force_stop",
                description = "强行停止一个应用（等价于设置里的「强行停止」）。需要 Shizuku/adb 或 Root；" +
                    "没有这些权限时本工具会明确告知，不要反复重试。",
                parameters = mapOf("app" to "必填，包名或应用名"),
                required = setOf("app"),
                riskLevel = ToolRiskLevel.HIGH,
            ),
        ) { args ->
            runAction(AppControlAction.FORCE_STOP, R.string.app_control_force_stop_action, args["app"])
        }

        registry.register(
            ToolRegistry.ToolDef(
                name = "app_clear_data",
                description = "清空一个应用的全部数据（不可撤销）。需要 Shizuku/adb 或 Root。执行前必须向用户确认。",
                parameters = mapOf("app" to "必填，包名或应用名"),
                required = setOf("app"),
                riskLevel = ToolRiskLevel.HIGH,
            ),
        ) { args ->
            runAction(AppControlAction.CLEAR_DATA, R.string.app_control_clear_data_action, args["app"])
        }

        registry.register(
            ToolRegistry.ToolDef(
                name = "app_uninstall",
                description = "卸载一个应用。需要 Shizuku/adb 或 Root；系统应用会被拒绝。执行前必须向用户确认。",
                parameters = mapOf("app" to "必填，包名或应用名"),
                required = setOf("app"),
                riskLevel = ToolRiskLevel.HIGH,
            ),
        ) { args ->
            runAction(AppControlAction.UNINSTALL, R.string.app_control_uninstall_action, args["app"])
        }
    }

    /** 统一的"执行 + 翻译"入口：入参为空的处理也只在这里写一次。 */
    private suspend fun runAction(action: AppControlAction, actionLabelRes: Int, rawApp: String?): String {
        val app = rawApp?.trim().orEmpty()
        return if (app.isEmpty()) {
            context.getString(R.string.app_control_need_app_arg)
        } else {
            describe(context.getString(actionLabelRes), app, controller.perform(action, app))
        }
    }

    /** 把 [AppActionResult] 翻成给模型看的一句话；做不到时说明缺哪一项能力。 */
    private fun describe(action: String, target: String, result: AppActionResult): String = when (result) {
        is AppActionResult.Success -> context.getString(
            R.string.app_control_ok,
            action,
            readableTarget(target),
            context.getString(
                when (result.via) {
                    ExecutionChannel.APP -> R.string.app_control_via_app
                    ExecutionChannel.SHELL -> R.string.app_control_via_shell
                    ExecutionChannel.ROOT -> R.string.app_control_via_root
                },
            ),
        )

        is AppActionResult.Unavailable -> context.getString(
            R.string.app_control_unavailable,
            action,
            readableTarget(target),
            context.getString(
                when (result.reason) {
                    UnavailableReason.INVALID_PACKAGE_NAME -> R.string.app_control_reason_invalid_name
                    UnavailableReason.PACKAGE_NOT_FOUND -> R.string.app_control_reason_not_found
                    UnavailableReason.NEED_SHELL_OR_ROOT -> R.string.app_control_reason_need_elevated
                    UnavailableReason.BLOCKED_SYSTEM_APP -> R.string.app_control_reason_system_app
                    UnavailableReason.BLOCKED_SELF -> R.string.app_control_reason_self
                },
            ),
        )

        is AppActionResult.Failed -> context.getString(
            R.string.app_control_failed,
            action,
            readableTarget(target),
            result.message,
        )
    }

    /**
     * 把工具入参（可能是包名、也可能是中文名）渲染成"名称(包名)"给人看。
     *
     * 查不到标签时原样返回入参——宁可显示得朴素，也不能因为 PackageManager 抛异常而让
     * 整条工具结果失败（那是"操作已成功但报告失败"的典型事故）。
     */
    private fun readableTarget(raw: String): String {
        if (!AppControlCore.isValidPackageName(raw)) return raw
        val label = runCatching {
            val pm = context.packageManager

            @Suppress("DEPRECATION")
            val info = pm.getApplicationInfo(raw, 0)
            pm.getApplicationLabel(info).toString().trim()
        }.getOrNull()
        return if (label.isNullOrBlank() || label == raw) raw else "$label($raw)"
    }
}

package io.zer0.muse.tools

import android.content.Context
import io.zer0.muse.terminal.TermuxChannel

/**
 * v2.2.1 Termux 通道:termux_exec — 在用户安装的 Termux(完整 Linux 环境)中执行命令。
 *
 * 与另外两个执行通道的分工：
 *  - terminal_exec:应用沙盒(App 自身权限),轻量文件/脚本;
 *  - device_shell:设备级命令行(需 Shizuku/Root);
 *  - termux_exec(本工具):完整 Linux 环境(bash/apt/pip/gcc/ffmpeg……),需用户安装 Termux
 *    并按 [TermuxChannel.describe] 的引导完成授权;未就绪时如实返回引导文案。
 *
 * HIGH 风险,走既有审批链。
 */
object TermuxExecTool {
    const val NAME = "termux_exec"
    private const val MAX_OUTPUT_CHARS = 64 * 1024

    fun toolDef() = ToolRegistry.ToolDef(
        name = NAME,
        description = "在 Termux(需用户安装并授权)的完整 Linux 环境中执行单条 bash 命令并返回输出。" +
            "适合 apt/pip/gcc/ffmpeg 等完整工具链任务;沙盒内轻量命令用 terminal_exec," +
            "设备级操控(点击/启停应用)用 device_shell。返回 exit 码与 stdout/stderr(各截断 64K 字符);" +
            "通道未就绪时返回配置引导。",
        parameters = mapOf(
            "command" to "必填。bash 命令,如 pkg list-installed / python myscript.py / ls ~",
            "timeout_ms" to "可选。超时毫秒,默认 60000,范围 1000-600000",
            "workdir" to "可选。工作目录(默认 Termux home)",
        ),
        required = setOf("command"),
        category = "built-in",
        parameterTypes = mapOf("timeout_ms" to "integer"),
        riskLevel = ToolRiskLevel.HIGH,
    )

    suspend fun execute(args: Map<String, String>, context: Context): String {
        val command = args["command"]?.trim().orEmpty()
        if (command.isEmpty()) return "Error: command is required."
        val timeoutMs = (args["timeout_ms"]?.toLongOrNull() ?: TermuxChannel.DEFAULT_TIMEOUT_MS)
            .coerceIn(1_000L, TermuxChannel.MAX_TIMEOUT_MS)
        val workdir = args["workdir"]?.trim()?.takeIf { it.isNotEmpty() }

        val channel = TermuxChannel.get(context)
        val availability = channel.availability()
        if (availability !is TermuxChannel.Availability.Ready) {
            return TermuxChannel.describe(availability)
        }

        val result = channel.exec(command, timeoutMs, workdir)
        if (result.error != null) {
            return buildString {
                append("Termux 执行未完成: ").append(result.error)
                append("\n如尚未配置 Termux 外部调用,请在 Termux 中执行 ")
                append("`echo 'allow-external-apps = true' >> ~/.termux/termux.properties` 并重启 Termux,")
                append("并在 系统设置→应用→Termux→附加权限 授予「运行 Termux 命令」。")
            }
        }
        return buildString {
            append("[Termux] exit=").append(result.exitCode).append('\n')
            if (result.stderr.isNotBlank()) {
                append("--- stderr ---\n").append(result.stderr.take(MAX_OUTPUT_CHARS)).append('\n')
            }
            val out = result.stdout
            if (out.isBlank()) {
                append("(无输出)")
            } else {
                append(out.take(MAX_OUTPUT_CHARS))
                if (out.length >= MAX_OUTPUT_CHARS) append("\n… (输出已截断)")
            }
        }
    }
}

package io.zer0.muse.tools

import io.zer0.common.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * v2.x 终端一期:terminal_exec 工具 — 在应用沙盒内执行单条 shell 命令。
 *
 * 与 [ShellSandboxTool](execute_shell:只读白名单) 和 device_shell(Shizuku/Root 设备级操控)
 * 互补:本工具是 **App 自身权限** 的终端,工作目录锁定应用工作区,用于文件处理、脚本、
 * 诊断等沙盒内任务。HIGH 风险,走既有审批链。
 */
object TerminalExecTool {
    const val NAME = "terminal_exec"
    private const val TAG = "TerminalExec"
    private const val MAX_OUTPUT_CHARS = 64 * 1024
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun toolDef() = ToolRegistry.ToolDef(
        name = NAME,
        description = "在应用沙盒内执行单条 shell 命令(工作目录为应用工作区,App 自身权限)。" +
            "适合文件处理/脚本/诊断;需要点击、启停应用等设备级操控时改用 device_shell(需 Shizuku/Root 授权)。" +
            "返回退出码与输出(上限 64K 字符);超时命令会被终止。",
        parameters = mapOf(
            "command" to "必填。单条 shell 命令,如 ls -la / cat notes.txt / mkdir -p out",
            "timeout_ms" to "可选。超时毫秒,默认 15000,范围 1000-60000",
        ),
        required = setOf("command"),
        category = "built-in",
        parameterTypes = mapOf("timeout_ms" to "integer"),
        riskLevel = ToolRiskLevel.HIGH,
    )

    suspend fun execute(args: Map<String, String>, workspaceDir: File): String {
        val command = args["command"]?.trim().orEmpty()
        if (command.isEmpty()) return "Error: command is required."
        val timeoutMs = (args["timeout_ms"]?.toIntOrNull() ?: 15_000).coerceIn(1_000, 60_000)
        return withContext(Dispatchers.IO) {
            val process = runCatching {
                ProcessBuilder("/system/bin/sh", "-c", command)
                    .directory(workspaceDir)
                    .redirectErrorStream(true)
                    .start()
            }.getOrElse { return@withContext "启动失败: ${it.message ?: "未知错误"}" }

            val output = StringBuilder()
            val readerJob = scope.launch {
                runCatching {
                    val buffer = CharArray(4096)
                    process.inputStream.bufferedReader().use { reader ->
                        while (true) {
                            val n = reader.read(buffer)
                            if (n < 0) break
                            if (output.length < MAX_OUTPUT_CHARS) {
                                output.append(buffer, 0, minOf(n, MAX_OUTPUT_CHARS - output.length))
                            }
                        }
                    }
                }.onFailure { Logger.d(TAG, "输出读取结束: ${it.message}") }
            }

            val finished = process.waitFor(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                readerJob.cancel()
                readerJob.join()
                val partial = output.toString()
                return@withContext buildString {
                    append("命令超时(${timeoutMs / 1000}s)已终止")
                    if (partial.isNotBlank()) append('\n').append(partial.take(4000))
                }
            }
            readerJob.join()
            val exit = process.exitValue()
            val text = output.toString()
            buildString {
                append("exit=").append(exit).append('\n')
                if (text.isBlank()) {
                    append("(无输出)")
                } else {
                    append(text.take(MAX_OUTPUT_CHARS))
                    if (text.length >= MAX_OUTPUT_CHARS) append("\n… (输出已截断)")
                }
            }
        }
    }
}

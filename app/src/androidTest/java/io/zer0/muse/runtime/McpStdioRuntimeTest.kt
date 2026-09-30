package io.zer0.muse.runtime

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.zer0.muse.mcp.McpClient
import io.zer0.muse.mcp.McpConnectionState
import io.zer0.muse.mcp.McpServerConfig
import io.zer0.muse.mcp.McpTransportType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.BufferedReader
import java.io.File

/**
 * P0 收尾：MCP stdio server 端到端验证（模拟器 connected 测试）。
 *
 * 链路：npm install @modelcontextprotocol/server-filesystem → 用内置 node 启动
 * server（stdio）→ JSON-RPC initialize 握手 → tools/list。
 *
 * 验证目标：市面上的 npm 系 MCP server 能在 Muse 沙盒里完整运行
 * （P1 stdio 传输实现的前置验收）。
 */
@RunWith(AndroidJUnit4::class)
class McpStdioRuntimeTest {

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun npmInstallAndMcpHandshake() {
        runBlocking {
            MuseRuntime.ensureData(ctx).getOrThrow()

            val work = File(MuseRuntime.runtimeDir(ctx), "mcp-e2e").apply {
                deleteRecursively()
                mkdirs()
            }
            File(work, "package.json").writeText("""{"name":"muse-mcp-e2e","private":true}""")

            // 1) npm install（网络下载，给足超时）
            val install = MuseRuntime.execNode(
                ctx = ctx,
                args = listOf(
                    MuseRuntime.npmCli(ctx).absolutePath,
                    "install",
                    "--no-audit",
                    "--no-fund",
                    "--loglevel=error",
                    "@modelcontextprotocol/server-filesystem",
                ),
                timeoutMs = 300_000,
                workdir = work,
            )
            val failMsg = "npm install 失败(stderr): ${install.stderr.take(800)}"
            assertEquals(failMsg, 0, install.exitCode)

            // 2) 定位 server 入口
            val pkgDir = File(work, "node_modules/@modelcontextprotocol/server-filesystem")
            val entry = listOf("dist/index.js", "build/index.js", "index.js")
                .map { File(pkgDir, it) }
                .firstOrNull { it.isFile }
            assertTrue("server 入口未找到（pkgDir=$pkgDir）", entry != null)

            // 3) 启动 server 并完成 MCP 握手
            val serverErr = StringBuilder()
            val proc = ProcessBuilder(
                MuseRuntime.nodeBinary(ctx).absolutePath,
                entry!!.absolutePath,
                work.absolutePath,
            )
                .directory(work)
                .apply {
                    environment().clear()
                    environment().putAll(MuseRuntime.buildEnv(ctx))
                }
                .start()
            Thread {
                runCatching {
                    proc.errorStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            synchronized(serverErr) {
                                if (serverErr.length < 8000) serverErr.appendLine(line)
                            }
                        }
                    }
                }
            }.start()

            try {
                val out = proc.outputStream
                val reader = proc.inputStream.bufferedReader()

                val initReq = """{"jsonrpc":"2.0","id":1,"method":"initialize","params":""" +
                    """{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"muse-e2e","version":"0.1"}}}"""
                out.write((initReq + "\n").toByteArray())
                out.flush()
                val initResp = readUntil(reader, "\"id\":1", 60_000)
                assertTrue("initialize 无响应/超时（stderr=${serverErr.take(400)}）", initResp != null)
                assertTrue("initialize 响应异常: ${initResp!!.take(300)}", initResp.contains("\"result\""))

                out.write("""{"jsonrpc":"2.0","method":"notifications/initialized"}""".plus("\n").toByteArray())
                out.write("""{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}""".plus("\n").toByteArray())
                out.flush()
                val toolsResp = readUntil(reader, "\"id\":2", 30_000)
                assertTrue("tools/list 无响应/超时（stderr=${serverErr.take(400)}）", toolsResp != null)
                assertTrue("tools/list 响应异常: ${toolsResp!!.take(300)}", toolsResp.contains("\"tools\""))
            } finally {
                proc.destroyForcibly()
            }
        }
    }

    @Test
    fun mcpClientStdioEndToEnd() {
        runBlocking {
            // P1-A 端到端：McpClient(STDIO) → 启动 npx server → initialize → listTools
            MuseRuntime.ensureData(ctx).getOrThrow()

            val config = McpServerConfig(
                id = "e2e-stdio",
                name = "e2e-filesystem",
                transportType = McpTransportType.STDIO,
                command = "npx -y @modelcontextprotocol/server-filesystem ${MuseRuntime.runtimeDir(ctx).absolutePath}",
                autoReconnect = false,
                requestTimeoutMs = 180_000,
            )
            val client = McpClient(config, appContext = ctx)
            try {
                client.start()
                val state = withTimeout(240_000) {
                    client.state.first {
                        it == McpConnectionState.CONNECTED || it == McpConnectionState.FAILED
                    }
                }
                assertEquals("连接状态异常", McpConnectionState.CONNECTED, state)
                val tools = client.listToolsOrNull()
                assertTrue("期望非空工具列表,实际: ${tools?.size}", tools != null && tools.isNotEmpty())
            } finally {
                client.close()
            }
        }
    }

    /** 逐行读取 stdout，直到出现包含 [needle] 的行；超时 / EOF 返回 null。 */
    private suspend fun readUntil(reader: BufferedReader, needle: String, timeoutMs: Long): String? = withContext(Dispatchers.IO) {
        try {
            withTimeout(timeoutMs) {
                async {
                    var found: String? = null
                    for (i in 0 until 50) {
                        val line = reader.readLine() ?: break
                        if (line.contains(needle)) {
                            found = line
                            break
                        }
                    }
                    found
                }.await()
            }
        } catch (e: Exception) {
            null
        }
    }
}

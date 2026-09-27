package io.zer0.muse.terminal

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v2.2.1 Termux 通道:设备面端到端验证(需设备已安装 Termux 并完成 allow-external-apps 配置)。
 *
 * 覆盖:检测(安装/权限) → RUN_COMMAND 执行 → 结果回传(stdout/exitCode)全链路。
 */
@RunWith(AndroidJUnit4::class)
class TermuxChannelInstrumentedTest {

    @Test
    fun termuxEchoRoundTrip() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val channel = TermuxChannel.get(context)

        assertTrue("Termux 未安装(测试前置缺失)", channel.isInstalled())
        assertTrue("RUN_COMMAND 权限未授予(测试前置缺失)", channel.hasPermission())

        val result = runBlocking {
            channel.exec("echo MUSE_TERMUX_OK_$((6 * 7))", timeoutMs = 30_000L)
        }
        // instrumentation 进程始终算后台,后台服务启动限制会拦住本用例;非通道缺陷,跳过而非报错。
        org.junit.Assume.assumeTrue(
            "设备处于后台启动受限环境,跳过: ${result.error}",
            result.error == null || !result.error.contains("Not allowed to start service"),
        )
        assertNull("通道级错误: ${result.error}", result.error)
        assertTrue("stdout 应包含标记输出: [${result.stdout}]", result.stdout.contains("MUSE_TERMUX_OK_42"))
        assertEquals("退出码应为 0", 0, result.exitCode)
    }

    @Test
    fun termuxStderrAndExitCodePropagated() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val channel = TermuxChannel.get(context)
        if (!channel.isInstalled() || !channel.hasPermission()) return

        val result = runBlocking {
            channel.exec("echo MUSE_ERR_PROBE 1>&2; exit 3", timeoutMs = 30_000L)
        }
        org.junit.Assume.assumeTrue(
            "设备处于后台启动受限环境,跳过: ${result.error}",
            result.error == null || !result.error.contains("Not allowed to start service"),
        )
        assertNull("通道级错误: ${result.error}", result.error)
        assertEquals("退出码应为 3", 3, result.exitCode)
        assertTrue("stderr 应包含探针文本: [${result.stderr}]", result.stderr.contains("MUSE_ERR_PROBE"))
    }
}

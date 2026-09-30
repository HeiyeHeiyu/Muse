package io.zer0.muse.terminal

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 终端二期:自编译 PTY(libpty.so)设备面验证。
 *
 * 真实 forkpty 往返:创建伪终端 → 经 pty 写命令 → 读回显/输出 → 取退出码。
 * 覆盖 JNI 边界:建进程 / 阻塞读写 / 等待退出 / 窗口尺寸 / 关 fd。
 */
@RunWith(AndroidJUnit4::class)
class PtyInstrumentedTest {

    @Test
    fun ptyEchoAndExitCodeRoundTrip() {
        assertTrue("libpty.so 应加载成功(Pty.available),loadError=${Pty.loadError}", Pty.available)
        val cwd = InstrumentationRegistry.getInstrumentation().targetContext.filesDir.absolutePath
        val pidArray = IntArray(1)
        val fd = Pty.createSubprocess("/system/bin/sh", cwd, pidArray, "", "")
        assertTrue("forkpty 应返回有效 fd,实际 $fd", fd >= 0)
        assertTrue("子进程 pid 应有效,实际 ${pidArray[0]}", pidArray[0] > 0)

        val output = StringBuilder()
        val reader = Thread {
            val buffer = ByteArray(4096)
            while (true) {
                val n = Pty.read(fd, buffer, 0, buffer.size)
                if (n <= 0) break
                synchronized(output) { output.append(String(buffer, 0, n, Charsets.UTF_8)) }
            }
        }
        reader.start()

        Thread.sleep(800) // 等交互式 sh 就绪
        val command = "echo MUSE_PTY_READY\nexit 7\n"
        val bytes = command.toByteArray(Charsets.UTF_8)
        val written = Pty.write(fd, bytes, 0, bytes.size)
        if (written != bytes.size) {
            val alive = runCatching {
                android.os.Process.sendSignal(pidArray[0], 0)
                true
            }.getOrDefault(false)
            val soFar = synchronized(output) { output.toString() }
            throw AssertionError(
                "写入失败: written=$written errno=${Pty.errnoValue()} childAlive=$alive " +
                    "available=${Pty.available(fd)} soFar=[$soFar]",
            )
        }

        reader.join(8000)
        Pty.closeFd(fd)

        val text = synchronized(output) { output.toString() }
        assertTrue("应包含命令回显: $text", text.contains("echo MUSE_PTY_READY"))
        assertTrue("应包含命令输出: $text", text.contains("MUSE_PTY_READY"))

        val exitCode = Pty.waitFor(pidArray[0])
        assertEquals("退出码应为 7(经 waitpid 解码)", 7, exitCode)
    }

    @Test
    fun windowSizeSyncDoesNotCrash() {
        if (!Pty.available) return
        val cwd = InstrumentationRegistry.getInstrumentation().targetContext.filesDir.absolutePath
        val pidArray = IntArray(1)
        val fd = Pty.createSubprocess("/system/bin/sh", cwd, pidArray, "", "")
        assertTrue(fd >= 0)
        Pty.setWindowSize(fd, 40, 120)
        Pty.setWindowSize(fd, 0, 0) // 非法值应被忽略而非崩溃
        Pty.closeFd(fd)
    }
}

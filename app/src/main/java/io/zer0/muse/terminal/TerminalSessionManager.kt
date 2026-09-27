package io.zer0.muse.terminal

import android.content.Context
import io.zer0.common.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * v2.x 终端一期:会话管理器 —— 常驻 shell 会话,输出流式回传 UI。
 *
 * 一期实现为**管道 shell**(`/system/bin/sh`,ProcessBuilder):零原生依赖、稳;
 * 工作目录锁定应用 workspace,环境注入 TERM/HOME。PTY 升级版(自编译 forkpty JNI)
 * 沿用同一接口,后续替换执行引擎即可(交互式全屏程序、Ctrl-C 信号将随 PTY 解锁)。
 *
 * 线程模型:输出读取在 IO 协程;回调 [Listener] 由调用方决定线程(UI 侧自行切主线程)。
 */
class TerminalSessionManager(private val appContext: Context) {

    interface Listener {
        fun onOutput(bytes: ByteArray)
        fun onExit(code: Int)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var process: Process? = null
    private var readerJob: Job? = null

    @Volatile
    private var listener: Listener? = null

    /** 会话是否在运行。 */
    val isRunning: Boolean
        @Synchronized get() = process?.isAlive == true

    fun setListener(l: Listener?) {
        listener = l
    }

    /** 若未运行则启动会话(幂等)。 */
    @Synchronized
    fun ensureStarted() {
        if (process?.isAlive == true) return
        startLocked()
    }

    /** 重启会话(先杀旧进程,再启动新进程)。 */
    @Synchronized
    fun restart() {
        killLocked()
        startLocked()
    }

    /** 向会话写入文本(自动按原样送 stdin;命令需自带换行)。 */
    fun write(text: String) {
        val proc = process ?: return
        scope.launch {
            runCatching {
                proc.outputStream.write(text.toByteArray(Charsets.UTF_8))
                proc.outputStream.flush()
            }.onFailure { Logger.w(TAG, "stdin 写入失败: ${it.message}") }
        }
    }

    @Synchronized
    private fun startLocked() {
        val workDir = File(appContext.filesDir, "workspace").apply { mkdirs() }
        val started = runCatching {
            ProcessBuilder("/system/bin/sh")
                .directory(workDir)
                .redirectErrorStream(true)
                .apply {
                    environment()["TERM"] = "xterm-256color"
                    environment()["HOME"] = workDir.absolutePath
                    environment()["PATH"] = "/system/bin:/system/xbin:$workDir/bin"
                }
                .start()
        }
        started.onFailure {
            Logger.w(TAG, "会话启动失败: ${it.message}")
            listener?.onExit(-1)
        }
        val proc = started.getOrNull() ?: return
        process = proc
        readerJob = scope.launch {
            val buffer = ByteArray(8192)
            try {
                val input = proc.inputStream
                while (isActive) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    listener?.onOutput(buffer.copyOf(n))
                }
            } catch (e: Exception) {
                Logger.d(TAG, "读取循环结束: ${e.message}")
            }
            val code = runCatching { proc.exitValue() }.getOrDefault(-1)
            listener?.onExit(code)
        }
    }

    @Synchronized
    private fun killLocked() {
        readerJob?.cancel()
        readerJob = null
        process?.let { proc ->
            runCatching { proc.destroy() }
            runCatching { proc.destroyForcibly() }
        }
        process = null
    }

    companion object {
        private const val TAG = "TerminalSession"
    }
}

/**
 * 进程级单例持有者:终端会话跨页面存活(App 存活期内),
 * 页面销毁只解绑 listener,不杀会话。
 */
object TerminalSessionStore {

    @Volatile
    private var manager: TerminalSessionManager? = null

    fun get(context: Context): TerminalSessionManager {
        manager?.let { return it }
        return synchronized(this) {
            manager ?: TerminalSessionManager(context.applicationContext).also { manager = it }
        }
    }
}

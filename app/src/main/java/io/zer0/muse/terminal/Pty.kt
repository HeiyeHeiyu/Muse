package io.zer0.muse.terminal

/**
 * v2.x 终端二期:PTY 桥(native 自编译,见 src/main/jni/pty.c)。
 *
 * 全部 fd 语义走 native 函数(读写均不经 FileDescriptor),规避隐藏 API 限制。
 * [available] 在 native 库未就绪时为 false,调用方应回退管道引擎。
 */
object Pty {

    /** native 库是否加载成功(NDK 未接入的构建里为 false)。 */
    val available: Boolean = runCatching {
        System.loadLibrary("pty")
        true
    }.getOrDefault(false)

    /**
     * 创建伪终端子进程。
     * @return 主端 fd(>=0);失败 -1。子进程 pid 写入 pidArray[0]。
     */
    external fun createSubprocess(execPath: String, cwd: String, pidArray: IntArray): Int

    /** 阻塞等待子进程退出,返回退出码(信号终止 128+signo;失败 -1)。 */
    external fun waitFor(pid: Int): Int

    /** 从主端读取(阻塞);<=0 表示 EOF/错误。 */
    external fun read(fd: Int, buffer: ByteArray, offset: Int, length: Int): Int

    /** 向主端写入;返回写入字节数(<0 出错)。 */
    external fun write(fd: Int, data: ByteArray, offset: Int, length: Int): Int

    /** 主端可读字节数;出错 -1。 */
    external fun available(fd: Int): Int

    /** 同步窗口尺寸(全屏程序排版用)。 */
    external fun setWindowSize(fd: Int, rows: Int, cols: Int)

    /** 关闭主端 fd(用于终止会话)。 */
    external fun closeFd(fd: Int)
}

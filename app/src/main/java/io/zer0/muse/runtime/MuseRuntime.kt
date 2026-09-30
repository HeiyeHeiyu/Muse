package io.zer0.muse.runtime

import android.content.Context
import io.zer0.common.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * v2.x 扩展运行时（P0 打样）：内置 Node.js 沙盒运行时管理器。
 *
 * ## 背景
 * Muse 的应用内沙盒（技能脚本 / MCP stdio server / 终端）需要真实运行时
 * （Node.js / npm）才能执行"市面上的"扩展生态。受 Android 10+ W^X 限制
 * （targetSdk 29+ 不可 exec 应用数据目录中的文件），运行时采用如下架构：
 *
 *  - **可执行部分**（node 主程序 + 依赖共享库）随 APK 发布在 jniLibs
 *    （extractNativeLibs=true，安装时解压到 nativeLibraryDir），
 *    此目录不受 W^X 限制，可直接 exec / dlopen；
 *  - **纯数据部分**（npm 等 JS 文件）打包为 assets/muse-runtime-assets.zip，
 *    首次使用时解压到 filesDir/muse-runtime/；
 *  - 共享库以 lib*.so 命名规范分发（libmuse_node.so / libssl.so / ...），
 *    内部 SONAME/DT_NEEDED 已重写、RPATH=$ORIGIN，自包含加载。
 *
 * 使用入口：[isReady] 检查 / [ensureData] 解压 / [execNode] 执行。
 */
@Suppress("TooManyFunctions") // 运行时门面：路径访问器 + 执行入口集合，属设计如此
object MuseRuntime {
    private const val TAG = "MuseRuntime"

    /** 子进程 stdout/stderr 各自的最大采集字节数。 */
    private const val MAX_OUTPUT_BYTES = 256 * 1024

    /** 内置 node 二进制（jniLibs 内，命名需符合 lib*.so 规范）。 */
    const val NODE_LIB_NAME = "libmuse_node.so"

    /** assets 中运行时数据包。 */
    private const val ASSETS_ZIP = "muse-runtime-assets.zip"

    /** 数据包版本标记（升级 assets 时同步修改，触发重新解压）。 */
    const val RUNTIME_DATA_VERSION = "npm-11.20.0"

    // ── 路径 ─────────────────────────────────────────────────────

    /** native 库目录（安装后解压出的 .so 所在目录）。 */
    fun nativeLibDir(ctx: Context): File = File(ctx.applicationInfo.nativeLibraryDir)

    /** 内置 node 可执行文件。 */
    fun nodeBinary(ctx: Context): File = File(nativeLibDir(ctx), NODE_LIB_NAME)

    /** 运行时数据根目录（filesDir/muse-runtime）。 */
    fun runtimeDir(ctx: Context): File = File(ctx.filesDir, "muse-runtime")

    /** npm 数据目录。 */
    fun npmDir(ctx: Context): File = File(runtimeDir(ctx), "npm")

    /** npm CLI 入口（JS 文件，由 node 执行）。 */
    fun npmCli(ctx: Context): File = File(npmDir(ctx), "bin/npm-cli.js")

    /** 运行时 HOME 目录（子进程工作目录）。 */
    fun homeDir(ctx: Context): File = File(runtimeDir(ctx), "home")

    /** 子进程临时目录（TMPDIR）。 */
    fun tmpDir(ctx: Context): File = File(runtimeDir(ctx), "tmp")

    /** 运行时 bin 目录（PATH 首位，预留给 symlink/垫片）。 */
    fun binDir(ctx: Context): File = File(runtimeDir(ctx), "bin")

    /** 运行时就绪（node 二进制存在且 npm 数据已解压）。 */
    fun isReady(ctx: Context): Boolean = nodeBinary(ctx).isFile && npmCli(ctx).isFile

    // ── 数据解压 ─────────────────────────────────────────────────

    private const val MARK_FILE = ".runtime-data-version"

    /**
     * 从 assets 解压运行时数据（幂等；版本标记命中时跳过）。
     */
    suspend fun ensureData(ctx: Context): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val rt = runtimeDir(ctx)
            val mark = File(rt, MARK_FILE)
            val installed = runCatching { mark.readText().trim() }.getOrNull()
            if (installed == RUNTIME_DATA_VERSION && npmCli(ctx).isFile) return@runCatching

            Logger.i(TAG, "解压运行时数据 ($installed -> $RUNTIME_DATA_VERSION)")
            npmDir(ctx).deleteRecursively()
            ctx.assets.open(ASSETS_ZIP).use { raw ->
                ZipInputStream(raw.buffered()).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val target = File(rt, entry.name)
                        val rootPath = rt.canonicalPath + File.separator
                        if (!target.canonicalPath.startsWith(rootPath)) {
                            throw SecurityException("zip 条目越界: ${entry.name}")
                        }
                        if (entry.isDirectory) {
                            target.mkdirs()
                        } else {
                            target.parentFile?.mkdirs()
                            target.outputStream().buffered().use { out -> zip.copyTo(out) }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
            mark.writeText(RUNTIME_DATA_VERSION)
            Logger.i(TAG, "运行时数据解压完成")
        }.onFailure { Logger.w(TAG, "运行时数据解压失败: ${it.message}", it) }
    }

    // ── 子进程环境 ───────────────────────────────────────────────

    /**
     * 构造受控子进程环境。
     *
     * 关键变量：
     *  - HOME/TMPDIR：指向运行时数据目录（Android 无可用默认值）
     *  - PATH：运行时 bin（预留）+ 系统路径
     *  - LD_LIBRARY_PATH：native 库目录（双保险；库内 RPATH=$ORIGIN 已覆盖）
     *  - SHELL：/system/bin/sh（Android 无 /bin/sh）
     */
    fun buildEnv(ctx: Context, extra: Map<String, String> = emptyMap()): Map<String, String> {
        listOf(homeDir(ctx), tmpDir(ctx), binDir(ctx)).forEach { it.mkdirs() }
        return buildMap {
            put("HOME", homeDir(ctx).absolutePath)
            put("TMPDIR", tmpDir(ctx).absolutePath)
            put("PATH", "${binDir(ctx).absolutePath}:/system/bin:/system/xbin")
            put("LD_LIBRARY_PATH", nativeLibDir(ctx).absolutePath)
            put("SHELL", "/system/bin/sh")
            put("TERM", "xterm-256color")
            putAll(extra)
        }
    }

    // ── 子进程执行 ───────────────────────────────────────────────

    /** 子进程执行结果。 */
    data class ExecResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
        val timedOut: Boolean = false,
    )

    /** 执行内置 node（args 追加在 node 路径之后）。 */
    suspend fun execNode(
        ctx: Context,
        args: List<String>,
        timeoutMs: Long = 30_000L,
        workdir: File? = homeDir(ctx),
        env: Map<String, String> = emptyMap(),
    ): ExecResult = exec(
        ctx = ctx,
        cmd = listOf(nodeBinary(ctx).absolutePath) + args,
        timeoutMs = timeoutMs,
        workdir = workdir,
        env = env,
    )

    /** 通用子进程执行（清空继承环境，使用 [buildEnv] 构造的环境）。 */
    suspend fun exec(
        ctx: Context,
        cmd: List<String>,
        timeoutMs: Long = 30_000L,
        workdir: File? = null,
        env: Map<String, String> = emptyMap(),
    ): ExecResult = withContext(Dispatchers.IO) {
        val pb = ProcessBuilder(cmd)
        workdir?.let {
            it.mkdirs()
            pb.directory(it)
        }
        pb.environment().clear()
        pb.environment().putAll(buildEnv(ctx, env))

        val process = pb.start()
        val outBuf = ByteArrayOutputStream()
        val errBuf = ByteArrayOutputStream()
        val tOut = Thread { runCatching { process.inputStream.copyToLimited(outBuf, MAX_OUTPUT_BYTES) } }
        val tErr = Thread { runCatching { process.errorStream.copyToLimited(errBuf, MAX_OUTPUT_BYTES) } }
        tOut.start()
        tErr.start()

        val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroyForcibly()
            process.waitFor(3, TimeUnit.SECONDS)
        }
        tOut.join(2_000)
        tErr.join(2_000)

        ExecResult(
            exitCode = if (finished) runCatching { process.exitValue() }.getOrDefault(-1) else -1,
            stdout = outBuf.toByteArray().toString(Charsets.UTF_8).trim(),
            stderr = errBuf.toByteArray().toString(Charsets.UTF_8).trim(),
            timedOut = !finished,
        )
    }

    private fun InputStream.copyToLimited(out: ByteArrayOutputStream, limit: Int) {
        val buf = ByteArray(64 * 1024)
        var total = 0
        while (true) {
            val n = read(buf)
            if (n < 0) break
            if (total < limit) {
                val w = minOf(n, limit - total)
                out.write(buf, 0, w)
                total += w
            }
        }
    }
}

package io.zer0.muse.automation.vdisplay

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * v2.2.1 虚拟屏:面向调用方(工具/Agent)的便捷客户端。
 *
 * 负责:ensure 服务端+虚拟屏、单帧截图、在虚拟屏里打开应用、销毁。
 * 输入注入不在本类(用 `input -d <displayId>` 走既有 shell 执行器)。
 */
class VirtualDisplayClient(
    private val context: Context,
    private val manager: VirtualDisplayServerManager,
) {
    data class DisplayHandle(val displayId: Int, val width: Int, val height: Int)

    /** 确保服务端 + 虚拟屏就绪(同尺寸复用);失败携带可读原因。 */
    suspend fun ensureDisplay(width: Int = DEFAULT_WIDTH, height: Int = DEFAULT_HEIGHT, dpi: Int = DEFAULT_DPI): Result<DisplayHandle> =
        withContext(Dispatchers.IO) {
            val proxy = manager.ensureStarted().getOrElse { return@withContext Result.failure(it) }
            val id =
                try {
                    proxy.ensureDisplay(width, height, dpi)
                } catch (e: Exception) {
                    -1
                }
            if (id < 0) {
                return@withContext Result.failure(
                    IllegalStateException("虚拟屏创建失败(详见 /data/local/tmp/muse-vd-server.log)"),
                )
            }
            manager.lastDisplayId = id
            Result.success(DisplayHandle(id, width, height))
        }

    /** 抓取一帧(JPEG 字节);失败返回 null。 */
    suspend fun screenshot(displayId: Int = manager.lastDisplayId): ByteArray? = withContext(Dispatchers.IO) {
        val proxy = manager.ensureStarted().getOrNull() ?: return@withContext null
        val id = if (displayId >= 0) displayId else manager.lastDisplayId
        if (id < 0) return@withContext null
        runCatching { proxy.requestScreenshot(id) }.getOrNull()
    }

    /** 在虚拟屏里打开应用:优先走服务端 shell 身份启动,服务端不可用时回退本应用 shell 通道。 */
    suspend fun openApp(packageName: String, displayId: Int = manager.lastDisplayId): Boolean = withContext(Dispatchers.IO) {
        if (!PACKAGE_REGEX.matches(packageName) || displayId < 0) return@withContext false
        manager.existingLiveProxy()?.let { proxy ->
            return@withContext runCatching { proxy.launchApp(packageName, displayId) }.getOrDefault(false)
        }
        // 回退:本应用 shell 通道(Shizuku/root)
        val resolved = manager.exec("cmd package resolve-activity --brief $packageName")
        val component =
            resolved.output
                .lineSequence()
                .map { it.trim() }
                .lastOrNull { it.contains('/') }
                ?: return@withContext false
        // 防注入:组件名出现 shell 元字符直接拒绝(包名已白名单,这里是双保险)
        if (component.any { it in INJECTION_CHARS }) return@withContext false
        manager.exec("am start --display $displayId -n $component").exitCode == 0
    }

    /** 销毁虚拟屏(服务端不存在时返回 false,不算错误)。 */
    suspend fun destroy(displayId: Int = manager.lastDisplayId): Boolean = withContext(Dispatchers.IO) {
        val proxy = manager.existingLiveProxy() ?: return@withContext false
        runCatching {
            proxy.destroyDisplay(displayId)
            if (displayId == manager.lastDisplayId) manager.lastDisplayId = -1
            true
        }.getOrDefault(false)
    }

    companion object {
        private const val DEFAULT_WIDTH = 720
        private const val DEFAULT_HEIGHT = 1280
        private const val DEFAULT_DPI = 320

        private val PACKAGE_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9_.]*$")

        /** shell 元字符集合(组件名出现即拒绝,防注入双保险)。 */
        private val INJECTION_CHARS = setOf(' ', ';', '&', '|', '<', '>', '\$', '`', '\'', '"', '\\')
    }
}

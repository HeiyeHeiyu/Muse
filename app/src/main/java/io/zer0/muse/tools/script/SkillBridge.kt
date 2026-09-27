package io.zer0.muse.tools.script

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import io.zer0.common.AppJson
import io.zer0.common.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.io.File

/**
 * F-17 + v2.2.1 大沙盒:JS 桥接执行器（__bridge__）。
 *
 * JS 函数可通过返回桥接请求对象获得受控能力（网络/沙盒文件/剪贴板/通知/设备信息）：
 * ```js
 * return { __bridge__: true, action: "http_get", params: { url: "..." } };
 * ```
 * WebView 沙盒禁用了 fetch/XHR（见 [io.zer0.muse.tools.JsSandbox.INIT_JS]），因此
 * Kotlin 侧在脚本执行结果处截获该请求，审计后执行对应的安全实现，再把结果作为技能
 * 返回值返回（request-response 模式，不破坏沙盒——所有 IO 都经过 Kotlin 侧）。
 *
 * 动作清单与门槛（与 [io.zer0.muse.data.plugin.PluginSecurityGate.allowedCapabilities] 对齐）：
 *  - `echo`：原样回显（默认可用，验证通路）
 *  - `http_get` / `http_post`：SSRF 防护 HTTP（需声明 network）
 *  - `fs_list` / `fs_read` / `fs_write` / `fs_delete`：沙盒目录内文件操作（需声明
 *    storage.read / storage.write；限宿主的 [Host.fsRoot]，路径 canonical 校验防逃逸）
 *  - `clipboard_read` / `clipboard_write`（需声明 clipboard.read / clipboard.write）
 *  - `notify`：应用内提示（需声明 notify）
 *  - `device_info`：设备基础信息（需声明 device.info）
 *
 * 调用方（skill 执行结果处理处）先调用 [tryHandle]：若返回值不含 `__bridge__`
 * 字段则返回 [HandleResult.NotBridge]（按普通结果处理）；否则返回执行产物/错误。
 */
object SkillBridge {

    private const val TAG = "SkillBridge"

    /** http_get 响应体读取硬上限(1MB),防脚本拉取超大内容撑爆内存(OOM 防护)。 */
    private const val MAX_BODY_BYTES = 1024 * 1024

    /** v2.2.1: http_post 请求体上限(256KB)。 */
    private const val MAX_REQUEST_BYTES = 256 * 1024

    /** B-2: 手动跟随重定向的最大跳数,防止重定向链无限延伸。 */
    private const val MAX_REDIRECTS = 5

    /** 默认放行的最小动作集（向后兼容：仅 echo + http_get）。 */
    val DEFAULT_ALLOWED_ACTIONS: Set<String> = setOf("echo", "http_get")

    /**
     * v2.2.1: 桥接宿主 —— 提供动作所需的系统上下文与沙盒根目录。
     *
     * @param context 剪贴板/提示等系统能力需要；这类动作之外的场景可为 null
     * @param fsRoot `fs_*` 动作的沙盒根（如 `workspace/plugins/<pluginId>`）；为空时 fs 动作拒绝
     */
    class Host(val context: Context?, val fsRoot: File?)

    /**
     * 桥接专用 HTTP 出口。DNS 解析、连接地址校验和逐跳重定向都在该出口内完成。
     * 不复用其他网络出口，避免把 DNS 策略泛化到聊天、插件或 WebView 流量。
     */
    private val bridgeHttpClient: SkillBridgeHttpClient by lazy { SkillBridgeHttpClient() }

    /**
     * 桥接处理结果密封类。
     *
     *  - [NotBridge]：返回值不含 `__bridge__`，调用方按普通脚本结果继续处理
     *  - [Output]：桥接请求已执行，产出结果 JSON 字符串
     *  - [Failure]：桥接请求解析或执行失败（含"action 不支持"）
     */
    sealed class HandleResult {
        object NotBridge : HandleResult()
        data class Output(val json: String) : HandleResult()
        data class Failure(val message: String) : HandleResult()
    }

    /**
     * 尝试处理一个 skill 脚本的执行返回值。
     *
     * @param valueJson 脚本返回值的 JSON 字符串（SkillEngineResult.Success.valueJson）
     * @param allowedActions 调用方按插件 manifest 声明计算出的放行动作集
     * @param host 桥接宿主（fs/剪贴板等动作需要）
     * @return 见 [HandleResult]
     */
    suspend fun tryHandle(
        valueJson: String,
        allowedActions: Set<String> = DEFAULT_ALLOWED_ACTIONS,
        host: Host? = null,
    ): HandleResult {
        val obj = runCatching {
            AppJson.parseToJsonElement(valueJson) as? JsonObject
        }.getOrNull() ?: return HandleResult.NotBridge
        val isBridge = (obj["__bridge__"] as? JsonPrimitive)?.booleanOrNull == true
        if (!isBridge) return HandleResult.NotBridge

        val action = (obj["action"] as? JsonPrimitive)?.contentOrNull
            ?: return HandleResult.Failure("__bridge__ 缺少 action 字段")
        if (action !in allowedActions) {
            return HandleResult.Failure("__bridge__ action '$action' 未获得插件声明的能力")
        }
        val params = obj["params"] as? JsonObject ?: buildJsonObject { }
        return when (action) {
            "echo" -> HandleResult.Output(params.toString())
            "http_get" -> withContext(Dispatchers.IO) { execHttpGet(params) }
            "http_post" -> withContext(Dispatchers.IO) { execHttpPost(params) }
            "fs_list" -> withContext(Dispatchers.IO) { execFsList(host, params) }
            "fs_read" -> withContext(Dispatchers.IO) { execFsRead(host, params) }
            "fs_write" -> withContext(Dispatchers.IO) { execFsWrite(host, params) }
            "fs_delete" -> withContext(Dispatchers.IO) { execFsDelete(host, params) }
            "clipboard_read" -> execClipboardRead(host)
            "clipboard_write" -> execClipboardWrite(host, params)
            "notify" -> execNotify(host, params)
            "device_info" -> HandleResult.Output(deviceInfoJson())
            else -> HandleResult.Failure("__bridge__ action '$action' 不受支持")
        }
    }

    // ── HTTP ─────────────────────────────────────────────────────────────

    /** 执行 http_get：SSRF 防护(含重定向逐跳校验) + 响应体上限。 */
    private fun execHttpGet(params: JsonObject): HandleResult {
        val url = (params["url"] as? JsonPrimitive)?.contentOrNull
            ?: return HandleResult.Failure("http_get 缺少 url 参数")
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return HandleResult.Failure("http_get url 仅支持 http/https 协议")
        }
        // 响应体读取上限,默认 1MB;用户请求值收敛到 [1, MAX_BODY_BYTES],
        // 防脚本拉取超大内容整读后撑爆上下文/内存(B-3)
        val maxSize = (params["max_size"] as? JsonPrimitive)?.contentOrNull
            ?.toIntOrNull()?.coerceIn(1, MAX_BODY_BYTES) ?: MAX_BODY_BYTES
        return execHttpGetWithRedirects(url, maxSize)
    }

    /** v2.2.1: 执行 http_post（不跟随重定向，fail-closed）。 */
    private fun execHttpPost(params: JsonObject): HandleResult {
        val url = (params["url"] as? JsonPrimitive)?.contentOrNull
            ?: return HandleResult.Failure("http_post 缺少 url 参数")
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return HandleResult.Failure("http_post url 仅支持 http/https 协议")
        }
        val body = (params["body"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        if (body.toByteArray(Charsets.UTF_8).size > MAX_REQUEST_BYTES) {
            return HandleResult.Failure("http_post 请求体超过上限(${MAX_REQUEST_BYTES / 1024}KB)")
        }
        val contentType = (params["content_type"] as? JsonPrimitive)?.contentOrNull
            ?.takeIf { it.isNotBlank() } ?: "text/plain; charset=utf-8"
        val maxSize = (params["max_size"] as? JsonPrimitive)?.contentOrNull
            ?.toIntOrNull()?.coerceIn(1, MAX_BODY_BYTES) ?: MAX_BODY_BYTES
        return try {
            val result = bridgeHttpClient.post(url, body, contentType, maxSize)
            HandleResult.Output(
                buildJsonObject {
                    put("status", JsonPrimitive(result.status))
                    put("body", JsonPrimitive(result.body))
                }.toString(),
            )
        } catch (e: Exception) {
            Logger.w(TAG, "http_post 桥接失败: ${e.message}")
            val message = if (e is SkillBridgeHttpClient.PinnedAddressException) {
                "http_post 拒绝访问内网/非公网地址: ${e.url}"
            } else {
                "http_post 请求失败: ${e.message ?: "网络异常"}"
            }
            HandleResult.Failure(message)
        }
    }

    /**
     * 手动跟随重定向的 http_get 执行。
     *
     * DNS 解析与连接级校验由 [SkillBridgeHttpClient] 完成；它为每一跳固定同一份已校验
     * 地址列表，避免“先校验 getAllByName、再由系统重新解析”的 TOCTOU。重定向仍由客户端
     * 禁用自动跟随并逐跳校验，任一跳失败即 fail-closed。
     */
    @Suppress("TooGenericExceptionCaught") // 底层网络异常统一转为用户可见失败信息
    private fun execHttpGetWithRedirects(startUrl: String, maxSize: Int): HandleResult {
        return try {
            val result = bridgeHttpClient.get(startUrl, maxSize, MAX_REDIRECTS)
            HandleResult.Output(
                buildJsonObject {
                    put("status", JsonPrimitive(result.status))
                    put("body", JsonPrimitive(result.body))
                }.toString(),
            )
        } catch (e: Exception) {
            Logger.w(TAG, "http_get 桥接失败: ${e.message}")
            val message = if (e is SkillBridgeHttpClient.PinnedAddressException) {
                "http_get 拒绝访问内网/非公网地址: ${e.url}"
            } else {
                "http_get 请求失败: ${e.message ?: "网络异常"}"
            }
            HandleResult.Failure(message)
        }
    }

    // ── 沙盒文件 ──────────────────────────────────────────────────────────

    private fun fsRootOf(host: Host?): File =
        host?.fsRoot ?: throw SkillBridgeFs.BridgeFsException("当前动作未提供沙盒目录")

    private fun execFsList(host: Host?, params: JsonObject): HandleResult {
        return try {
            val root = fsRootOf(host)
            val path = paramString(params, "path")
            val entries = SkillBridgeFs.list(root, path)
            HandleResult.Output(
                buildJsonObject {
                    put("path", JsonPrimitive(path.ifEmpty { "." }))
                    put(
                        "entries",
                        buildJsonArray {
                            entries.forEach { entry ->
                                add(
                                    buildJsonObject {
                                        put("name", JsonPrimitive(entry.name))
                                        put("dir", JsonPrimitive(entry.dir))
                                        put("size", JsonPrimitive(entry.size))
                                    },
                                )
                            }
                        },
                    )
                }.toString(),
            )
        } catch (e: Exception) {
            fsFailure("fs_list", e)
        }
    }

    private fun execFsRead(host: Host?, params: JsonObject): HandleResult {
        return try {
            val root = fsRootOf(host)
            val path = paramString(params, "path")
            if (path.isEmpty()) return HandleResult.Failure("fs_read 缺少 path 参数")
            val content = SkillBridgeFs.read(root, path)
            HandleResult.Output(
                buildJsonObject {
                    put("path", JsonPrimitive(path))
                    put("content", JsonPrimitive(content))
                }.toString(),
            )
        } catch (e: Exception) {
            fsFailure("fs_read", e)
        }
    }

    private fun execFsWrite(host: Host?, params: JsonObject): HandleResult {
        return try {
            val root = fsRootOf(host)
            val path = paramString(params, "path")
            if (path.isEmpty()) return HandleResult.Failure("fs_write 缺少 path 参数")
            val content = paramString(params, "content")
            val written = SkillBridgeFs.write(root, path, content)
            HandleResult.Output(
                buildJsonObject {
                    put("path", JsonPrimitive(path))
                    put("written_bytes", JsonPrimitive(written))
                }.toString(),
            )
        } catch (e: Exception) {
            fsFailure("fs_write", e)
        }
    }

    private fun execFsDelete(host: Host?, params: JsonObject): HandleResult {
        return try {
            val root = fsRootOf(host)
            val path = paramString(params, "path")
            if (path.isEmpty()) return HandleResult.Failure("fs_delete 缺少 path 参数")
            SkillBridgeFs.delete(root, path)
            HandleResult.Output(
                buildJsonObject {
                    put("path", JsonPrimitive(path))
                    put("deleted", JsonPrimitive(true))
                }.toString(),
            )
        } catch (e: Exception) {
            fsFailure("fs_delete", e)
        }
    }

    private fun fsFailure(action: String, e: Exception): HandleResult =
        HandleResult.Failure(
            if (e is SkillBridgeFs.BridgeFsException) {
                "$action 被拒绝: ${e.message}"
            } else {
                "$action 失败: ${e.message ?: "未知错误"}"
            },
        )

    // ── 系统能力 ──────────────────────────────────────────────────────────

    private suspend fun execClipboardRead(host: Host?): HandleResult {
        val context = host?.context ?: return HandleResult.Failure("桥接宿主缺少 Context,无法读取剪贴板")
        return withContext(Dispatchers.Main) {
            try {
                val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    ?: return@withContext HandleResult.Failure("剪贴板服务不可用")
                val text = manager.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                HandleResult.Output(buildJsonObject { put("text", JsonPrimitive(text)) }.toString())
            } catch (e: Exception) {
                HandleResult.Failure("clipboard_read 失败: ${e.message ?: "未知错误"}")
            }
        }
    }

    private suspend fun execClipboardWrite(host: Host?, params: JsonObject): HandleResult {
        val context = host?.context ?: return HandleResult.Failure("桥接宿主缺少 Context,无法写入剪贴板")
        val text = paramString(params, "text")
        return withContext(Dispatchers.Main) {
            try {
                val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    ?: return@withContext HandleResult.Failure("剪贴板服务不可用")
                manager.setPrimaryClip(ClipData.newPlainText("muse-skill", text))
                HandleResult.Output("""{"ok":true}""")
            } catch (e: Exception) {
                HandleResult.Failure("clipboard_write 失败: ${e.message ?: "未知错误"}")
            }
        }
    }

    private suspend fun execNotify(host: Host?, params: JsonObject): HandleResult {
        val context = host?.context ?: return HandleResult.Failure("桥接宿主缺少 Context,无法发送提示")
        val title = paramString(params, "title")
        val body = paramString(params, "body").ifBlank { title }
        if (body.isBlank()) return HandleResult.Failure("notify 缺少 title/body")
        return withContext(Dispatchers.Main) {
            try {
                android.widget.Toast.makeText(context, body, android.widget.Toast.LENGTH_SHORT).show()
                HandleResult.Output("""{"ok":true}""")
            } catch (e: Exception) {
                HandleResult.Failure("notify 失败: ${e.message ?: "未知错误"}")
            }
        }
    }

    private fun deviceInfoJson(): String = buildJsonObject {
        put("brand", JsonPrimitive(Build.BRAND))
        put("model", JsonPrimitive(Build.MODEL))
        put("android", JsonPrimitive(Build.VERSION.RELEASE))
        put("sdk", JsonPrimitive(Build.VERSION.SDK_INT))
    }.toString()

    private fun paramString(params: JsonObject, key: String): String =
        (params[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
}

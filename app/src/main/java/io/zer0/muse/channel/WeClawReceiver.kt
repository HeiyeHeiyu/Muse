package io.zer0.muse.channel

import android.content.Context
import io.zer0.common.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * v2.0: 微信 ClawBot(iLink)接收器 — 长轮询循环。
 *
 * 存在启用中的 WECLAW 渠道时循环调用 getupdates(长轮询约 30s):
 * 用户消息写入 [ChannelInbox](触发自动回复链路),context_token 写入
 * [WeClawContextCache] 供回发携带。配置保存/删除后由 UI 调用 [restart]。
 */
class WeClawReceiver(
    private val channelManager: ChannelManager,
    private val context: Context,
    private val appScope: CoroutineScope,
) {
    private var job: Job? = null

    /** 启动轮询(幂等;已有循环先停再起)。 */
    fun restart() {
        job?.cancel()
        job = appScope.launch {
            ChannelInbox.attach(context)
            pollLoop()
        }
    }

    /** 停止轮询(App 关闭或渠道被删时)。 */
    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun pollLoop() {
        var buffer = ""
        while (currentCoroutineContext().isActive) {
            channelManager.refresh()
            val config = channelManager.channels.value.firstOrNull {
                it.enabled && it.platform == ChannelPlatform.WECLAW && it.appSecret.isNotBlank()
            }
            if (config == null) {
                Logger.i(TAG, "无启用中的 ClawBot 渠道,接收循环退出")
                return
            }
            val updates = WeClawClient.getUpdates(config.appSecret, buffer).getOrNull()
            if (updates == null) {
                // 网络异常/服务端错误:退避后重试
                delay(RETRY_DELAY_MS)
                continue
            }
            buffer = updates.buffer.ifBlank { buffer }
            updates.messages.forEach { msg ->
                WeClawContextCache.put(msg.fromUserId, msg.contextToken)
                val media = msg.media
                if (media == null) {
                    ChannelInbox.record("WECLAW", msg.fromUserId, msg.text, "")
                } else {
                    handleMediaMessage(msg, media)
                }
            }
        }
    }

    /**
     * v2.0.1: 媒体消息处理 — 图片下载(CDN + AES 解密)并压缩入库;
     * 语音使用服务端转写(parseMessages 已填充);
     * v2.x (B4): 视频/文件下载到私有目录并记录本地路径(文本占位升级为含文件名/路径)。
     */
    private suspend fun handleMediaMessage(msg: WeClawClient.InboundMsg, media: WeClawClient.MediaRef) {
        val from = msg.fromUserId
        when (media.kind) {
            "image" -> {
                val bytes = withTimeoutOrNull(MEDIA_DOWNLOAD_TIMEOUT_MS) {
                    WeClawClient.downloadMedia(media).getOrNull()
                }
                val base64 = bytes?.let { ChannelMediaUtils.toCompactImageBase64(it) }
                if (base64 != null) {
                    ChannelInbox.record(
                        platform = "WECLAW",
                        from = from,
                        text = "[图片]",
                        rawPayload = "",
                        mediaKind = "image",
                        mediaBase64 = base64,
                    )
                } else {
                    Logger.w(TAG, "图片下载或解码失败(from=$from)")
                    ChannelInbox.record("WECLAW", from, "[图片(未能获取)]", "")
                }
            }
            "voice" -> {
                // iLink 语音自带服务端 ASR 转写;无转写时给占位。
                ChannelInbox.record("WECLAW", from, msg.text.ifBlank { "[语音]" }, "")
            }
            // v2.x (B4): 视频/文件 — 下载 + 落盘 + 记录(含文件名与路径)
            "video" -> handleBinaryMedia(msg, media, kind = "video", label = "[视频]")
            else -> handleBinaryMedia(msg, media, kind = "file", label = "[文件]")
        }
    }

    /**
     * v2.x (B4): 视频/文件入站 — 下载并解密到 [MEDIA_DIR],记录含文件名与本地路径。
     *
     * v2.x 遗留收尾:改为**流式**下载(边下边写文件),不再一次性读入内存;
     * [MAX_MEDIA_SAVE_BYTES] 保护在流内生效(超出立即中止并删除半成品)。
     * 失败/超限/超时降级为占位文本(不抛异常,不阻断后续轮询)。
     */
    private suspend fun handleBinaryMedia(
        msg: WeClawClient.InboundMsg,
        media: WeClawClient.MediaRef,
        kind: String,
        label: String,
    ) {
        val from = msg.fromUserId
        val target = File(
            File(context.filesDir, MEDIA_DIR),
            "${System.currentTimeMillis()}_${defaultMediaFileName(media, kind)}",
        )
        val result = withTimeoutOrNull(MEDIA_DOWNLOAD_TIMEOUT_MS) {
            WeClawClient.downloadMediaToFile(media, target, MAX_MEDIA_SAVE_BYTES)
        }
        if (result == null) {
            Logger.w(TAG, "媒体下载超时(kind=$kind, from=$from)")
            ChannelInbox.record("WECLAW", from, "$label(未能获取)", "", mediaKind = kind)
            return
        }
        val written = result.getOrNull()
        if (written == null) {
            val error = result.exceptionOrNull()
            if (error is MediaTooLargeException) {
                Logger.w(TAG, "媒体超出大小上限(kind=$kind, from=$from)")
                ChannelInbox.record(
                    "WECLAW",
                    from,
                    "$label(超出大小上限 ${MAX_MEDIA_SAVE_BYTES / 1024 / 1024}MB,未落盘)",
                    "",
                    mediaKind = kind,
                )
            } else {
                Logger.w(TAG, "媒体下载失败(kind=$kind, from=$from, err=${error?.message})")
                ChannelInbox.record("WECLAW", from, "$label(未能获取)", "", mediaKind = kind)
            }
            return
        }
        val displayName = media.fileName.ifBlank { target.name }
        ChannelInbox.record(
            platform = "WECLAW",
            from = from,
            text = "$label $displayName",
            rawPayload = "",
            mediaKind = kind,
            mediaPath = target.absolutePath,
        )
    }

    /** v2.x 遗留收尾:清洗文件名;无名字媒体补默认名(扩展名按类型)。 */
    private fun defaultMediaFileName(media: WeClawClient.MediaRef, kind: String): String {
        val sanitized = sanitizeFileName(media.fileName)
        if (sanitized.isNotBlank()) return sanitized
        return "${kind}${if (kind == "video") ".mp4" else ".bin"}"
    }

    /** v2.x (B4): 清洗文件名 — 只保留末段,剔除路径分隔符与不可见控制字符。 */
    private fun sanitizeFileName(raw: String): String =
        raw.substringAfterLast('/').substringAfterLast('\\')
            .filter { it.code >= 0x20 && it.code != 0x7F }
            .trim()
            .take(120)

    companion object {
        private const val TAG = "WeClawReceiver"

        /** 出错重试退避(毫秒)。 */
        private const val RETRY_DELAY_MS = 5_000L

        /** v2.0.1: 媒体下载超时(毫秒)。 */
        private const val MEDIA_DOWNLOAD_TIMEOUT_MS = 60_000L

        /** v2.x (B4): 视频/文件落盘目录(私有 filesDir 下)。 */
        private const val MEDIA_DIR = "channel_media"

        /** v2.x (B4): 视频/文件落盘大小上限(32MB),超出仅记录占位。 */
        private const val MAX_MEDIA_SAVE_BYTES = 32L * 1024 * 1024
    }
}

package io.zer0.muse.ui.knowledge

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// v2.3.1: 单文件导入的前置守卫 —— 主知识库页(KnowledgeScreen)与知识库管理页
// (KnowledgeBaseManagePage)共用同一份判定,避免两条导入路径规则漂移。
//
// 背景(2026-09 反馈):守卫此前只加在管理页,主页面仍把二进制当文本读入 →
// 乱码写库并建立向量索引,用户看到的是「导入成功」。纯名称规则(压缩包扩展名)
// 见 ZipImportPolicy.isArchiveFile;本文件只放需要 ContentResolver 的嗅探。

/**
 * 嗅探文件头部是否含 NUL 字节(二进制特征),防止压缩包/可执行文件被当文本索引。
 * 读取失败时按「非二进制」处理,不阻断正常导入。
 */
internal suspend fun looksBinary(uri: Uri, context: Context): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val head = ByteArray(512)
            val n = input.read(head)
            n > 0 && (0 until n).any { head[it] == 0.toByte() }
        } ?: false
    }.getOrDefault(false)
}

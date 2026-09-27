package io.zer0.muse.tools.script

import java.io.File
import java.io.IOException

/**
 * v2.2.1 大沙盒:技能/插件桥接的沙盒文件系统操作 —— 全部路径锚定在调用方给定的沙盒根目录内。
 *
 * 安全:
 *  - 路径解析后做 canonical 包含校验,拒绝绝对路径与 `..` 上跳,符号链接逃逸同样被 canonical
 *    检查拦截;
 *  - 读/写均有体积上限,目录列表有数量上限;
 *  - 纯逻辑(不依赖 Android Framework),便于单元测试。
 */
internal object SkillBridgeFs {

    /** 单文件读取上限 1MB。 */
    const val MAX_READ_BYTES = 1 shl 20

    /** 单次写入上限 1MB。 */
    const val MAX_WRITE_BYTES = 1 shl 20

    /** 目录列表返回条目上限。 */
    const val MAX_LIST_ENTRIES = 200

    /** 沙盒文件操作失败(路径越界/类型不符/超限),信息可直接反馈给脚本调用方。 */
    class BridgeFsException(message: String) : IOException(message)

    /** 沙盒内条目(供 fs_list 序列化)。 */
    data class Entry(val name: String, val dir: Boolean, val size: Long)

    /**
     * 把沙盒内相对路径解析为绝对 [File];任何越界(绝对路径/`..`/符号链接逃逸)都抛异常。
     */
    fun resolve(root: File, path: String): File {
        val cleaned = path.trim().replace('\\', '/')
        if (cleaned.isEmpty()) return root.canonicalFile
        if (cleaned.startsWith("/") || cleaned.split('/').any { it == ".." }) {
            throw BridgeFsException("非法路径(仅允许沙盒内相对路径): $path")
        }
        val rootPath = root.canonicalFile
        val target = File(root, cleaned).canonicalFile
        val rootPrefix = rootPath.path + File.separator
        if (target != rootPath && !target.path.startsWith(rootPrefix)) {
            throw BridgeFsException("路径越界: $path")
        }
        return target
    }

    /** 列目录(最多 [MAX_LIST_ENTRIES] 条)。 */
    fun list(root: File, path: String): List<Entry> {
        val dir = resolve(root, path)
        if (!dir.isDirectory) throw BridgeFsException("不是目录: $path")
        return dir.listFiles().orEmpty()
            .sortedBy { it.name }
            .take(MAX_LIST_ENTRIES)
            .map { Entry(it.name, it.isDirectory, if (it.isFile) it.length() else 0L) }
    }

    /** 读文本文件(UTF-8,上限 [MAX_READ_BYTES])。 */
    fun read(root: File, path: String): String {
        val file = resolve(root, path)
        if (!file.isFile) throw BridgeFsException("不是文件: $path")
        if (file.length() > MAX_READ_BYTES) {
            throw BridgeFsException("文件超过读取上限(${MAX_READ_BYTES / 1024}KB): $path")
        }
        return file.readText(Charsets.UTF_8)
    }

    /** 写文本文件(UTF-8,自动建目录,上限 [MAX_WRITE_BYTES]);返回写入字节数。 */
    fun write(root: File, path: String, content: String): Int {
        val bytes = content.toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_WRITE_BYTES) {
            throw BridgeFsException("内容超过写入上限(${MAX_WRITE_BYTES / 1024}KB)")
        }
        val file = resolve(root, path)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
        return bytes.size
    }

    /** 删除文件或空目录;拒绝删除沙盒根目录。 */
    fun delete(root: File, path: String): Boolean {
        val file = resolve(root, path)
        if (file == root.canonicalFile) throw BridgeFsException("不允许删除沙盒根目录")
        if (!file.exists()) throw BridgeFsException("不存在: $path")
        if (file.isDirectory && (file.listFiles()?.isNotEmpty() == true)) {
            throw BridgeFsException("目录非空,不能删除: $path")
        }
        return file.delete()
    }
}

package io.zer0.muse.backup

internal fun readBackupFileStores(paths: Iterable<String>, read: (name: String) -> String?): Map<String, String> {
    val stores = linkedMapOf<String, String>()
    paths.forEach { name ->
        read(name)?.let { stores[name] = it }
    }
    return stores
}

/**
 * 恢复文件型备份存储的 fail-fast 边界。
 *
 * 文件型存储与三个 Room 库共享同一个跨存储恢复账本；任何一个白名单文件写失败
 * 都必须向上传播，才能触发 [BackupService] 的 recovery point 回滚。
 */
internal suspend fun restoreBackupFileStores(
    stores: Map<String, String>,
    allowedPaths: Set<String>,
    write: suspend (name: String, content: String) -> Unit,
) {
    stores.forEach { (name, content) ->
        if (name in allowedPaths) {
            write(name, content)
        }
    }
}

package io.zer0.muse.ui.knowledge

/** v2.x: zip 导入条目类型。 */
internal enum class ZipEntryKind { TEXT, PARSED, SKIP }

/**
 * v2.x: ZIP 压缩包导入策略 — 白名单分类、junk 过滤、上限与临时文件命名。
 *
 * 纯逻辑(无 Android 依赖),独立成文件便于单测;解压/落盘/索引流程
 * 见 KnowledgeBaseManagePage 的 importZipBundle。
 */
internal object ZipImportPolicy {
    /** 单次导入最多处理的条目数。 */
    const val MAX_ENTRIES = 500

    /** 单条目解压上限(字节,解析类落盘用);超限条目按失败处理。 */
    const val MAX_ENTRY_BYTES = 50L * (1L shl 20)

    /** 单次导入累计消耗上限(字节口径,文本条目按字符近似)。 */
    const val MAX_TOTAL_BYTES = 300L * (1L shl 20)

    /** 文本条目读取字符上限(约合 chars*2≈bytes 口径);超限截断并加标记。 */
    const val MAX_TEXT_CHARS = 10_000_000

    /** 文本类白名单(md/markdown/txt/csv/json/log)。 */
    private val TEXT_EXTS = setOf("md", "markdown", "txt", "csv", "json", "log")

    /** 解析类白名单(pdf/docx/doc/epub/pptx)。 */
    private val PARSED_EXTS = setOf("pdf", "docx", "doc", "epub", "pptx")

    fun extensionOf(name: String): String = name.substringAfterLast('.', "").lowercase()

    /** 目录 / 隐藏文件 / macOS 资源叉等不应视为文档的条目。 */
    fun isJunk(name: String): Boolean {
        val base = name.substringAfterLast('/')
        val dirOrBlank = name.isBlank() || name.endsWith('/')
        val macMeta = name.startsWith("__MACOSX/")
        val hidden = base.isEmpty() || base.startsWith('.')
        return dirOrBlank || macMeta || hidden
    }

    fun classify(name: String): ZipEntryKind {
        val ext = extensionOf(name)
        return when (ext) {
            in TEXT_EXTS -> ZipEntryKind.TEXT
            in PARSED_EXTS -> ZipEntryKind.PARSED
            else -> ZipEntryKind.SKIP
        }
    }

    /** 文本条目的 doc.fileType(与单文件导入口径一致)。 */
    fun textFileType(name: String): String {
        val ext = extensionOf(name)
        return when (ext) {
            "md", "markdown" -> "md"
            "csv" -> "csv"
            "json" -> "json"
            else -> "txt"
        }
    }

    /** 解析类条目的 doc.fileType。 */
    fun parsedFileType(name: String): String {
        val ext = extensionOf(name)
        return when (ext) {
            "pdf" -> "pdf"
            "docx", "doc" -> "docx"
            "epub" -> "epub"
            "pptx" -> "pptx"
            else -> "txt"
        }
    }

    /** 临时落盘文件名:保留扩展名供解析器识别,剔除路径与非法字符,并限长。 */
    fun sanitizeTempName(entryName: String): String {
        val base = entryName.substringAfterLast('/')
        val dot = base.lastIndexOf('.')
        val rawStem = if (dot > 0) base.substring(0, dot) else base
        val stem = rawStem.take(48).replace(Regex("[\\\\/:*?\"<>|\\r\\n]"), "_")
        val ext = if (dot > 0) base.substring(dot + 1).take(12) else ""
        return if (ext.isEmpty()) "entry_$stem" else "entry_$stem.$ext"
    }
}

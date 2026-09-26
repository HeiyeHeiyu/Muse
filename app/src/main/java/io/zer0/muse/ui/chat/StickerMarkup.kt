package io.zer0.muse.ui.chat

/**
 * v2.x: 表情包标记解析器。
 *
 * 模型在回复正文里写 `[[sticker:分类名]]`,渲染层把它替换为一张表情包图片卡片;
 * 模型看不到图片内容,只按用户导入时设置的文件夹名(分类)选择。
 *
 * 职责:
 *  - [findMarkers]:找出所有完整标记(跳过 ``` 代码围栏内,避免误伤代码示例)
 *  - [findOpenMarkerStart]:检测尾部未闭合标记(流式渲染时吞掉半截文本,不露 `[[sticker:无`)
 *  - [split]:把正文切成"文本段 + 表情段"供渲染层逐段渲染
 *
 * 容错策略:分类名匹配失败时调用方应忽略该标记(宁可少发,不乱发);
 * 未闭合标记一律吞掉不显示(模型偶尔写漏/被截断时优雅降级)。
 */
object StickerMarkup {
    private const val OPEN = "[["
    private const val PREFIX = "sticker:"

    /** 完整标记:[[sticker:分类名]](分类名 1-32 字符,不含方括号/换行)。 */
    private val MARKER_REGEX =
        Regex(
            """\[\[\s*sticker\s*:\s*([^\[\]\n]{1,32}?)\s*\]\]""",
            RegexOption.IGNORE_CASE,
        )

    /** 未闭合标记的容错窗口:[[ 之后超过该长度仍无闭合,视为普通文本。 */
    private const val OPEN_MARKER_MAX_TAIL = 40

    /** 一条完整标记在父文本中的位置(start 含、end 不含)。 */
    data class Marker(val start: Int, val end: Int, val category: String)

    /** 渲染分段:普通文本段 或 表情段。 */
    sealed interface Segment {
        data class Text(val text: String) : Segment

        data class Sticker(val category: String) : Segment
    }

    /**
     * 找出所有完整标记(按出现顺序)。
     *
     * 跳过 ``` 代码围栏内的行——代码示例里的 `[[sticker:...]]` 字面量不应被转换。
     */
    fun findMarkers(text: String): List<Marker> {
        if (!text.contains(OPEN)) return emptyList()
        val result = mutableListOf<Marker>()
        var offset = 0
        var inFence = false
        for (line in text.split('\n')) {
            if (line.trimStart().startsWith("```")) {
                inFence = !inFence
            } else if (!inFence) {
                for (m in MARKER_REGEX.findAll(line)) {
                    val category = m.groupValues[1].trim()
                    if (category.isNotEmpty()) {
                        result.add(
                            Marker(
                                start = offset + m.range.first,
                                end = offset + m.range.last + 1,
                                category = category,
                            ),
                        )
                    }
                }
            }
            offset += line.length + 1 // +1: '\n'
        }
        return result
    }

    /**
     * 检测"末尾未闭合标记"的起点位置(渲染时从该处截断,避免露出半截 `[[sticker:开`)。
     *
     * 判定条件:文本里最后一个 `[[` 之后(≤[OPEN_MARKER_MAX_TAIL] 字符)没有 `]]`,
     * 且该段内容符合 `sticker:` 的前缀形态(或为空,即刚打出 `[[`)。
     * 返回 -1 表示无未闭合标记。
     */
    fun findOpenMarkerStart(text: String): Int {
        var searchEnd = text.length
        while (searchEnd > 1) {
            val idx = text.lastIndexOf(OPEN, searchEnd - 1)
            if (idx < 0) return -1
            val after = text.substring(idx + OPEN.length)
            if (after.length > OPEN_MARKER_MAX_TAIL || after.contains("]]")) {
                // 太长或已闭合:这个 [[ 不是半截标记,继续往前找
                searchEnd = idx
                continue
            }
            val trimmed = after.trimStart().lowercase()
            if (trimmed.isEmpty() || PREFIX.startsWith(trimmed) || trimmed.startsWith(PREFIX)) {
                return idx
            }
            searchEnd = idx
        }
        return -1
    }

    /**
     * 把正文切成渲染分段。
     *
     * - 已闭合标记切成 [Segment.Sticker](分类名原样携带,由渲染层做容错匹配);
     * - 末尾未闭合标记整体吞掉(不显示半截文本);
     * - 无标记时返回单个 [Segment.Text](调用方零额外开销直通原渲染);
     * - 标记前后与段尾的空白换行会被修剪,避免表情卡片上下出现过大空隙。
     */
    fun split(text: String): List<Segment> {
        if (!text.contains(OPEN)) return listOf(Segment.Text(text))
        val openStart = findOpenMarkerStart(text)
        val display = if (openStart >= 0) text.substring(0, openStart).trimEnd() else text
        val markers = findMarkers(display)
        if (markers.isEmpty()) return listOf(Segment.Text(display))
        val out = mutableListOf<Segment>()
        var cursor = 0
        for (m in markers) {
            if (m.start > cursor) {
                val t = display.substring(cursor, m.start).trim()
                if (t.isNotBlank()) out.add(Segment.Text(t))
            }
            out.add(Segment.Sticker(m.category))
            cursor = m.end
        }
        if (cursor < display.length) {
            val t = display.substring(cursor).trim()
            if (t.isNotBlank()) out.add(Segment.Text(t))
        }
        return out.ifEmpty { listOf(Segment.Text(display)) }
    }
}

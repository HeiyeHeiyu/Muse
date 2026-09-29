package io.zer0.muse.transformer

import io.zer0.ai.core.UIMessage

/**
 * v2.3.2: 进程内"已摘要水位线"(上下文压缩审查 findings C / D)。
 *
 * 记录"某会话的哪些消息已被摘要覆盖",供两处使用:
 *  - **C 摘要复用**:会话切回 / 重载后,同一段历史不必再花一次 LLM 调用重新摘要
 *    (压缩结果原先只存在内存的 `_messages` 里,从 DB 重载会拿回全量历史 → 又要重压一遍,
 *    白花一次 LLM 调用,并可能带来最长 20s 的首字延迟)
 *  - **D 阻断还原**:对话树重建(`mergeRebuildMessages`)会把旧树里的原文合并回来,
 *    与摘要共存导致 token 双计;按这里的 coveredIds 过滤即可
 *
 * 仅进程内有效(跨进程复用需要把摘要落库,属 DB 迁移范畴,单独排期);
 * 容量按写入时序淘汰,避免长跑进程无界增长。
 */
object CompressionSummaryStore {
    /** 一次压缩的结果:摘要文本 + 被它覆盖(已从上下文移除)的消息 id。 */
    data class Entry(val summary: String, val coveredIds: Set<String>)

    /** 最多记住的会话数(超出淘汰最早写入的)。 */
    private const val MAX_ENTRIES = 32

    private val entries = LinkedHashMap<String, Entry>()

    /** 记下一次压缩(会话 id / 摘要为空、或没有覆盖任何消息时忽略)。 */
    @Synchronized
    fun remember(sessionId: String?, coveredIds: Set<String>, summary: String) {
        if (sessionId.isNullOrBlank() || summary.isBlank() || coveredIds.isEmpty()) return
        // 重新插入即"最近写入",超出容量时淘汰最早的
        entries.remove(sessionId)
        entries[sessionId] = Entry(summary, coveredIds)
        if (entries.size > MAX_ENTRIES) {
            entries.keys.take(entries.size - MAX_ENTRIES).toList().forEach { entries.remove(it) }
        }
    }

    /** 读取会话的最近一次压缩记录(没有则返回 null)。 */
    @Synchronized
    fun entry(sessionId: String?): Entry? = sessionId?.let { entries[it] }

    /** 清空某会话的记录(会话被删除 / 历史被清空时调用)。 */
    @Synchronized
    fun clear(sessionId: String) {
        entries.remove(sessionId)
    }

    /** 仅供单测:清空全部记录。 */
    @Synchronized
    internal fun clearAllForTest() {
        entries.clear()
    }
}

/**
 * v2.3.2 (C): 能否复用缓存摘要。
 *
 * 命中条件:该会话已有缓存摘要,且本次待压缩区间**全部**落在其覆盖范围内。
 * 只覆盖一部分时不能复用 —— 那会把未被覆盖的消息也一并替换掉(等于丢历史),
 * 这种情况交给压缩器重新摘要(新摘要会覆盖更大的区间)。
 */
internal fun reusableSummary(entry: CompressionSummaryStore.Entry?, toCompress: List<UIMessage>): String? = entry?.summary?.takeIf {
    toCompress.isNotEmpty() && toCompress.all { message -> message.id.toString() in entry.coveredIds }
}

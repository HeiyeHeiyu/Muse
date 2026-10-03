package io.zer0.memory.summary

import androidx.room.withTransaction

/**
 * 会话上下文检查点的读取与有效性判定。
 *
 * 检查点本身只是一行数据；真正决定"它还算不算数"的是它引用的历史是否还在。
 * 这里把判定收敛到一处，避免每个调用方各自实现一套（并各自漏掉边界情况）。
 */
class ContextCheckpointReader(
    private val db: MemoryDb,
) {
    private fun dao(): ContextCheckpointDao = db.contextCheckpointDao()

    /** 读取某会话的检查点（没有则 null）。 */
    suspend fun get(sessionId: String?): ContextCheckpointEntity? = sessionId?.takeIf { it.isNotBlank() }?.let { dao().get(it) }

    /**
     * 读取检查点并校验它引用边界那条消息是否仍在给定历史里。
     *
     * - 检查点不存在 → null
     * - [ContextCheckpointEntity.lastCoveredMessageId] 为空 → 无从校验，退回"按 seq 边界"使用
     *   （消息 id 在极旧数据里可能不是 UUID，此时 [ContextCheckpointEntity.coveredSeq] 仍是有效上界）
     * - 边界消息已不在历史中 → **判定作废并删除**：历史被改动过（删除/清空/回滚），
     *   继续用这份摘要会把"已经不在上下文里的内容"又说成发生过，属于丢历史，宁可不用
     *
     * @param knownMessageIds 当前会话历史中可见的消息 id（字符串形式）
     */
    suspend fun getValid(sessionId: String?, knownMessageIds: Collection<String>): ContextCheckpointEntity? {
        val entity = get(sessionId)
        val boundaryId = entity?.lastCoveredMessageId.orEmpty()
        // 边界为空 → 无从校验，退回"按 seq 边界"使用（消息 id 在极旧数据里可能不是 UUID，
        // 此时 coveredSeq 仍是有效上界）；边界已不在历史里 → 作废并删除。
        val boundaryStillPresent = boundaryId.isBlank() || boundaryId in knownMessageIds
        if (entity != null && !boundaryStillPresent) {
            dao().deleteById(entity.sessionId)
        }
        return if (entity != null && boundaryStillPresent) entity else null
    }

    /**
     * 写入检查点。
     *
     * 覆盖式：同一会话只保留一条，新一次压缩把旧检查点与新增区间一起改写成新的。
     */
    suspend fun put(entity: ContextCheckpointEntity) {
        db.withTransaction { dao().upsert(entity) }
    }

    /** 删除某会话的检查点（会话删除 / 历史清空时调用）。 */
    suspend fun clear(sessionId: String) {
        db.withTransaction { dao().deleteById(sessionId) }
    }
}

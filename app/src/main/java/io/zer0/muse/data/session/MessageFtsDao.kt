package io.zer0.muse.data.session

import androidx.room.Dao
import androidx.room.Query
import androidx.room.SkipQueryVerification

/**
 * R-DB-05: messages_fts raw FTS DAO。
 *
 * messages_fts 不在 @Database entities 列表(Room KSP 无法为 FTS5/raw vtable 做 schema
 * 校验),因此与 KnowledgeChunkFtsDao 一致,所有方法使用 @SkipQueryVerification。
 */
@Dao
@Suppress("TooManyFunctions")
interface MessageFtsDao {

    /** FTS4 ngram 全文搜索(JOIN messages + sessions)。 */
    @SkipQueryVerification
    @Query("""
        SELECT
            m.id as messageId,
            m.sessionId as sessionId,
            m.content as content,
            m.role as role,
            m.createdAt as createdAt,
            s.title as sessionTitle
        FROM messages_fts
        JOIN messages m ON messages_fts.message_id = m.id
        JOIN sessions s ON m.sessionId = s.id
        WHERE content_ngram MATCH :matchQuery
        ORDER BY m.createdAt DESC
        LIMIT 50
    """)
    suspend fun searchFts(matchQuery: String): List<MessageSearchJoin>

    /** FTS5 原文全文搜索(JOIN messages rowid + sessions)。 */
    @SkipQueryVerification
    @Query("""
        SELECT
            m.id as messageId,
            m.sessionId as sessionId,
            m.content as content,
            m.role as role,
            m.createdAt as createdAt,
            s.title as sessionTitle
        FROM messages_fts
        JOIN messages m ON messages_fts.rowid = m.rowid
        JOIN sessions s ON m.sessionId = s.id
        WHERE messages_fts MATCH :matchQuery
        ORDER BY m.createdAt DESC
        LIMIT 50
    """)
    suspend fun searchFts5(matchQuery: String): List<MessageSearchJoin>

    /** 插入 FTS4 ngram 索引。 */
    @SkipQueryVerification
    @Query("INSERT INTO messages_fts(message_id, content_ngram) VALUES(:messageId, :contentNgram)")
    suspend fun insertFts(messageId: String, contentNgram: String)

    /** 删除指定消息的 FTS 索引。 */
    @SkipQueryVerification
    @Query("DELETE FROM messages_fts WHERE message_id = :messageId")
    suspend fun deleteFts(messageId: String)

    /** 删除指定会话全部消息的 FTS 索引。 */
    @SkipQueryVerification
    @Query("DELETE FROM messages_fts WHERE message_id IN (SELECT id FROM messages WHERE sessionId = :sessionId)")
    suspend fun deleteFtsBySession(sessionId: String)

    /** 删除指定会话内 createdAt >= fromCreatedAt 的消息 FTS 索引。 */
    @SkipQueryVerification
    @Query(
        """
        DELETE FROM messages_fts 
        WHERE message_id IN (
            SELECT id FROM messages WHERE sessionId = :sessionId AND createdAt >= :fromCreatedAt
        )
        """,
    )
    suspend fun deleteFtsBySessionAndCreatedAt(sessionId: String, fromCreatedAt: Long)

    /** 清空 FTS 索引(rebuild 用)。 */
    @SkipQueryVerification
    @Query("DELETE FROM messages_fts")
    suspend fun clearFts()

    /** messages_fts 表行数(ensureFtsIndexConsistent 比较用)。 */
    @SkipQueryVerification
    @Query("SELECT COUNT(*) FROM messages_fts")
    suspend fun countFts(): Int

    /** FTS5 external content 全量重建索引。 */
    @SkipQueryVerification
    @Query("INSERT INTO messages_fts(messages_fts) VALUES('rebuild')")
    suspend fun rebuildFts5()
}

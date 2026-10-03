package io.zer0.memory.summary

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * 上下文检查点 DAO。
 *
 * 一个会话至多一条检查点（覆盖式写入），所以没有"列表/分页"类查询——
 * 读取方永远只关心"这个会话当前的边界在哪、摘要是什么"。
 */
@Dao
interface ContextCheckpointDao {

    /** 写入/覆盖检查点。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ContextCheckpointEntity)

    /** 读取某会话的检查点（没有则返回 null）。 */
    @Query("SELECT * FROM context_checkpoints WHERE session_id = :sessionId")
    suspend fun get(sessionId: String): ContextCheckpointEntity?

    /** 删除某会话的检查点（会话被删除 / 历史被清空 / 检查点被判定作废时调用）。 */
    @Query("DELETE FROM context_checkpoints WHERE session_id = :sessionId")
    suspend fun deleteById(sessionId: String)

    /** 清空全部（备份恢复覆盖写入前调用）。 */
    @Query("DELETE FROM context_checkpoints")
    suspend fun deleteAll()

    /** 备份导出用。 */
    @Query("SELECT * FROM context_checkpoints")
    suspend fun getAll(): List<ContextCheckpointEntity>
}

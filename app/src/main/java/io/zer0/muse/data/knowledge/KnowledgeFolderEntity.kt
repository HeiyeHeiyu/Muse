@file:Suppress("TooManyFunctions")

package io.zer0.muse.data.knowledge

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/**
 * v2.4.6: 知识库文件夹实体 — 让「文件夹」成为可独立存在（含空文件夹）的一等公民。
 *
 * 背景:此前文件夹是**虚拟路径**（存在 [KnowledgeDocEntity.metadataJson] 的 folderPath 里），
 * 一个没有任何文档的文件夹没有任何承载物，于是「新建文件夹」无法实现。
 * 本表为每个文件夹存一条记录，使空文件夹也能在文件树里显示。
 *
 * 世界观（v2.4.6 起）:
 *  - 知识库(KB)= 根;文件夹 = 根下的子目录,路径形如 `合同` / `合同/2026`。
 *  - 文件夹同时是**检索隔离单位**:绑定/访问某文件夹路径时,检索范围限定为该路径
 *    及其子路径下的全部文档,与其它文件夹互不串。
 *  - [path] 为 KB 内相对路径,根(= KB 本身)用空串表示,不落表。
 *
 * 与文档的关系:文档仍在自己的 metadataJson 里保留 folderPath（单一真源）,本表只负责
 * 「即使没有文档,这个文件夹也存在」。读取文件夹树时合并两处来源。
 *
 * @param id 主键,格式 "kbfolder-{kbId}-{path}"(同一 KB 下 path 唯一)
 * @param kbId 所属知识库 id
 * @param path KB 内相对路径(归一化后,无首尾斜杠),非空
 * @param name 文件夹显示名(路径末段)
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
@Serializable
@Entity(
    tableName = "knowledge_folders",
    indices = [
        Index(value = ["kb_id"]),
        Index(value = ["kb_id", "path"], unique = true),
    ],
)
data class KnowledgeFolderEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "kb_id") val kbId: String,
    val path: String,
    val name: String,
    @ColumnInfo(name = "created_at", defaultValue = "0") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at", defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),
)

@Dao
interface KnowledgeFolderDao {
    /** 列出全部显式文件夹(空文件夹也会返回)。 */
    @Query("SELECT * FROM knowledge_folders ORDER BY kb_id, path")
    fun observeAll(): Flow<List<KnowledgeFolderEntity>>

    @Query("SELECT * FROM knowledge_folders WHERE kb_id = :kbId ORDER BY path")
    fun observeByKb(kbId: String): Flow<List<KnowledgeFolderEntity>>

    @Query("SELECT * FROM knowledge_folders WHERE kb_id = :kbId ORDER BY path")
    suspend fun getByKb(kbId: String): List<KnowledgeFolderEntity>

    /** 全量导出(备份用)。 */
    @Query("SELECT * FROM knowledge_folders ORDER BY kb_id, path")
    suspend fun getAll(): List<KnowledgeFolderEntity>

    @Query("SELECT * FROM knowledge_folders WHERE kb_id = :kbId AND path = :path")
    suspend fun getByPath(kbId: String, path: String): KnowledgeFolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(folder: KnowledgeFolderEntity)

    /** 删除某路径及其全部子路径的文件夹记录(用于「删除文件夹并含子目录」)。 */
    @Query("DELETE FROM knowledge_folders WHERE kb_id = :kbId AND (path = :path OR path LIKE :path || '/%')")
    suspend fun deleteByPathRecursive(kbId: String, path: String)

    /** 取某路径及其子路径的全部文件夹(重命名/移动子树时在内存里重算 path 与 name)。 */
    @Query("SELECT * FROM knowledge_folders WHERE kb_id = :kbId AND (path = :path OR path LIKE :path || '/%')")
    suspend fun getByPathRecursive(kbId: String, path: String): List<KnowledgeFolderEntity>

    @Query("DELETE FROM knowledge_folders WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM knowledge_folders WHERE kb_id = :kbId")
    suspend fun deleteByKb(kbId: String)

    @Query("DELETE FROM knowledge_folders")
    suspend fun deleteAll()
}
